package com.evp.identity.session;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real RS256 access/ID tokens and ES256 DPoP proofs, real filter and H2 session records. */
@SpringBootTest(properties = "evp.session.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BoundSessionIntegrationTests.TestClock.class)
class BoundSessionIntegrationTests {
    static final String ISSUER = "http://localhost:8081/realms/evp";
    static final String ORIGIN = "http://localhost:5173";
    static RSAKey signing;
    static ECKey device;
    static HttpServer jwks;
    static {
        try {
            signing = new RSAKeyGenerator(2048).keyID("sessions").generate();
            device = new ECKeyGenerator(Curve.P_256).generate();
            byte[] data = new JWKSet(signing.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext("/keys", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, data.length);
                try (var out = exchange.getResponseBody()) { out.write(data); }
            });
            jwks.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/keys");
    }
    @AfterAll static void closeKeys() { jwks.stop(0); }
    static class MutableClock extends Clock {
        Instant time = Instant.now();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
    }
    @TestConfiguration static class TestClock {
        @Bean @Primary MutableClock sessionClock() { return new MutableClock(); }
    }
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired BoundSessionRepository records;
    String sid;
    String token;
    String id;
    @BeforeEach void setup() throws Exception {
        records.deleteAll();
        clock.time = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        sid = UUID.randomUUID().toString();
        token = signed(false, "voter", "evp-backend", clock.time, 600);
        id = signed(true, "voter", "evp-frontend", clock.time, 600);
    }
    String signed(boolean identity, String subject, String audience, Instant authTime, long ttl) throws Exception {
        var builder = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience(audience)
            .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(ttl)))
            .jwtID(UUID.randomUUID().toString()).claim("sid", sid)
            .claim("preferred_username", "voter1");
        if (identity) builder.claim("auth_time", authTime.getEpochSecond());
        else builder.claim("cnf", Map.of("jkt", device.computeThumbprint().toString()));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sessions").build(), builder.build());
        jwt.sign(new RSASSASigner(signing));
        return jwt.serialize();
    }
    MockHttpServletRequestBuilder request(String method, String path) throws Exception {
        var proof = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("dpop+jwt")).jwk(device.toPublicJWK()).build(),
            new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(new Date())
                .claim("htm", method).claim("htu", "http://localhost" + path)
                .claim("ath", BoundSessionService.hash(token)).build());
        proof.sign(new ECDSASigner(device));
        return ("GET".equals(method) ? get(path) : post(path)).header("Authorization", "DPoP " + token)
            .header("DPoP", proof.serialize()).header("Origin", ORIGIN);
    }
    MockHttpServletRequestBuilder start() throws Exception {
        return request("POST", "/api/session/start").contentType("application/json")
            .content("{\"idToken\":\"" + id + "\"}");
    }
    Cookie open() throws Exception {
        var result = mvc.perform(start()).andExpect(status().isNoContent()).andReturn();
        String header = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.startsWith("__Host-evp-session="));
        assertTrue(header.contains("Secure")); assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Strict")); assertTrue(header.contains("Path=/"));
        assertFalse(header.contains("Domain="));
        return new Cookie(BoundSessionService.COOKIE, header.split(";", 2)[0].split("=", 2)[1]);
    }
    @Test void cookieAndMatchingDpopTokenAreRequired() throws Exception {
        Cookie cookie = open();
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(request("GET", "/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void anotherTokenCannotUseTheCookie() throws Exception {
        Cookie cookie = open();
        token = signed(false, "other", "evp-backend", clock.time, 600);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void cookieIsDeletedAndTokenCannotReopenRevokedSession() throws Exception {
        Cookie cookie = open();
        mvc.perform(request("POST", "/api/session/end").cookie(cookie)).andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(start().cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void idleExpiresAt90SecondsEvenWithoutBrowserTimer() throws Exception {
        Cookie cookie = open();
        clock.time = clock.time.plusSeconds(90);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(request("POST", "/api/session/activity").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void readsDoNotKeepTheSessionAlive() throws Exception {
        Cookie cookie = open();
        clock.time = clock.time.plusSeconds(80);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isOk());
        clock.time = clock.time.plusSeconds(10);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void reportedActivityExtendsIdleButNotAbsoluteExpiry() throws Exception {
        Cookie cookie = open();
        clock.time = clock.time.plusSeconds(80);
        mvc.perform(request("POST", "/api/session/activity").cookie(cookie)).andExpect(status().isNoContent());
        clock.time = clock.time.plusSeconds(80);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isOk());
        clock.time = clock.time.plusSeconds(500);
        mvc.perform(request("GET", "/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void crossOriginMutationIsRefused() throws Exception {
        Cookie cookie = open();
        mvc.perform(request("POST", "/api/session/end").cookie(cookie)
            .with(req -> { req.removeHeader("Origin"); req.addHeader("Origin", "https://evil.example"); return req; }))
            .andExpect(status().isForbidden());
    }
    @Test void staleAuthenticationCannotBootstrap() throws Exception {
        id = signed(true, "voter", "evp-frontend", clock.time.minusSeconds(120), 600);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void wrongIdTokenSubjectCannotBootstrap() throws Exception {
        id = signed(true, "other", "evp-frontend", clock.time, 600);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void wrongIdTokenAudienceCannotBootstrap() throws Exception {
        id = signed(true, "voter", "other-client", clock.time, 600);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void accessTokenLongerThanTenMinutesCannotBootstrap() throws Exception {
        token = signed(false, "voter", "evp-backend", clock.time, 1200);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void refreshedTokenCannotReopenTheSameAuthenticationEvent() throws Exception {
        Cookie cookie = open();
        mvc.perform(request("POST", "/api/session/end").cookie(cookie)).andExpect(status().isNoContent());
        token = signed(false, "voter", "evp-backend", clock.time, 600);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void missingOriginCannotOpenSession() throws Exception {
        mvc.perform(start().with(req -> { req.removeHeader("Origin"); return req; }))
            .andExpect(status().isForbidden());
    }
    @Test void missingAuthenticationTimeCannotOpenSession() throws Exception {
        var original = SignedJWT.parse(id);
        var claims = new HashMap<String,Object>(original.getJWTClaimsSet().toJSONObject());
        claims.remove("auth_time");
        var changed = new SignedJWT(original.getHeader(), JWTClaimsSet.parse(claims));
        changed.sign(new RSASSASigner(signing));
        id = changed.serialize();
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void differentKeycloakSessionCannotOpenSession() throws Exception {
        sid = "other-session";
        id = signed(true, "voter", "evp-frontend", clock.time, 600);
        mvc.perform(start()).andExpect(status().isUnauthorized());
    }
    @Test void anonymousBallotStillNeedsNoSession() throws Exception {
        mvc.perform(post("/api/ballots").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()); // Reached validation, not blocked by session/auth.
    }
}
