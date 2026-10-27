package com.evp.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.evp.support.ApiTestBase;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real application/filter chain; mocked JWT only for account budgeting, not cryptographic validation. */
@TestPropertySource(properties={"evp.rate-limit.enabled=true", "evp.enrolment.min-response-ms=1"})
@Import(RateLimitApiTests.RateClock.class)
class RateLimitApiTests extends ApiTestBase {
    private static final AtomicLong NOW = new AtomicLong(100000);
    private static final String ENROL="/api/public/enrolment-check";
    private static final String BODY="""
        {"fullName":"Nobody Example","dateOfBirth":"1990-01-01","postcode":"3000"}
        """;
    @TestConfiguration static class RateClock {
        @Bean @Primary RequestRateLimiter testLimiter() {
            return new RequestRateLimiter(NOW::get, Duration.ofMinutes(1),10000);
        }
    }
    @BeforeEach void freshWindow() { NOW.addAndGet(60001); }
    private MockHttpServletRequestBuilder request(String path,String ip,String body) {
        return post(path).with(r->{r.setRemoteAddr(ip);return r;})
            .contentType("application/json").content(body);
    }
    @Test void twentyFirstEnrolmentRequestReturns429AndRetryAfter() throws Exception {
        for(int i=0;i<20;i++) mvc.perform(request(ENROL,"192.0.2.1",BODY)).andExpect(status().isOk());
        mvc.perform(request(ENROL,"192.0.2.1",BODY))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
        assertThat(voters.count()).isZero();
        NOW.addAndGet(60000);
        mvc.perform(request(ENROL,"192.0.2.1",BODY)).andExpect(status().isOk());
    }
    @Test void fakeForwardingHeadersCannotBypassIpBudget() throws Exception {
        for(int i=0;i<20;i++) mvc.perform(request(ENROL,"192.0.2.2",BODY)
            .header("X-Forwarded-For","198.51.100."+i).header("Forwarded","for=198.51.100."+i))
            .andExpect(status().isOk());
        mvc.perform(request(ENROL,"192.0.2.2",BODY).header("X-Forwarded-For","203.0.113.99"))
            .andExpect(status().isTooManyRequests());
        mvc.perform(request(ENROL,"192.0.2.3",BODY)).andExpect(status().isOk());
    }
    @Test void anonymousBallotsAreLimitedWithoutAccountOrBodyTracking() throws Exception {
        for(int i=0;i<30;i++) mvc.perform(request("/api/ballots","192.0.2.4","{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(request("/api/ballots","192.0.2.4","{}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(request(ENROL,"192.0.2.4",BODY)).andExpect(status().isOk());
    }
    @Test void accountBudgetSurvivesIpChangesButDoesNotBlockAnotherAccount() throws Exception {
        for(int i=0;i<10;i++) mvc.perform(request("/api/tokens","192.0.2."+(10+i),"{}")
            .with(jwt().jwt(j->j.subject("voter-a")))).andExpect(status().isBadRequest());
        mvc.perform(request("/api/tokens","192.0.2.30","{}")
            .with(jwt().jwt(j->j.subject("voter-a")))).andExpect(status().isTooManyRequests());
        mvc.perform(request("/api/tokens","192.0.2.30","{}")
            .with(jwt().jwt(j->j.subject("voter-b")))).andExpect(status().isBadRequest());
        NOW.addAndGet(60000);
        mvc.perform(request("/api/tokens","192.0.2.30","{}")
            .with(jwt().jwt(j->j.subject("voter-a")))).andExpect(status().isBadRequest());
    }
    @Test void failedAuthenticationConsumesIpBudgetBeforeAuthentication() throws Exception {
        for(int i=0;i<30;i++) mvc.perform(request("/api/session/start","192.0.2.40","{}")
            .header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(request("/api/session/start","192.0.2.40","{}")
            .header("Authorization","Bearer invalid")).andExpect(status().isTooManyRequests());
    }
    @Test void attackerCannotConsumeAccountBudgetUsingUnverifiedUsername() throws Exception {
        for(int i=0;i<12;i++) mvc.perform(request("/api/tokens","192.0.2.50","{\"username\":\"voter-a\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(request("/api/tokens","192.0.2.51","{}")
            .with(jwt().jwt(j->j.subject("voter-a")))).andExpect(status().isBadRequest());
    }
    @Test void encodedPathCannotBypassBudget() throws Exception {
        for(int i=0;i<30;i++) mvc.perform(request("/api/ballots","192.0.2.70","{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(java.net.URI.create("/api/%62allots"))
            .with(r->{r.setRemoteAddr("192.0.2.70");return r;})
            .contentType("application/json").content("{}"))
            .andExpect(status().isTooManyRequests());
    }
    @Test void healthAndCorsPreflightRemainAvailableWhenBudgetIsExhausted() throws Exception {
        for(int i=0;i<20;i++) mvc.perform(request(ENROL,"192.0.2.60",BODY)).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(options(ENROL).header("Origin","http://localhost:5173")
            .header("Access-Control-Request-Method","POST")).andExpect(status().isOk());
    }
}
