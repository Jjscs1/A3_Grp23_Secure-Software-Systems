package com.evp.validation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end check of the SEC-06 layer through MockMvc, using a throw-away probe controller so it
 * does not depend on other teams' DTOs. Security auto-config is excluded on purpose: this test is
 * about input handling, and RequestGuardFilter runs before Spring Security in production anyway.
 */
@WebMvcTest(controllers = ValidationApiTest.Probe.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({RequestGuardFilter.class, ValidationProblemAdvice.class, StrictJsonConfig.class, ValidationApiTest.Probe.class})
@ActiveProfiles("validation-probe")
class ValidationApiTest {

    record ProbeRequest(
            @NotNull @SafeText(kind = InputRules.TextKind.NAME, min = 2, max = 50) String name,
            @NotNull @IsoPastDate String dob) { }

    @RestController
    @Profile("validation-probe")
    static class Probe {
        @PostMapping("/api/validation-probe")
        String post(@Valid @RequestBody ProbeRequest r) {
            return "ok";
        }

        @GetMapping("/api/validation-probe")
        String get(@RequestParam String division) {
            return "ok";
        }
    }

    @Autowired
    MockMvc mvc;

    private static final String VALID = "{\"name\":\"Jane Citizen\",\"dob\":\"1990-01-01\"}";

    @Test
    void validRequestPasses() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk());
    }

    @Test
    void scriptInNameIs400WithoutEcho() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>alert(1)</script>\",\"dob\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issues[0].field").value("name"))
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    void sqlInjectionInNameIs400() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x'; DROP TABLE voter;--\",\"dob\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void impossibleDateIs400() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\",\"dob\":\"2020-02-31\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownFieldIsRejected_massAssignment() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\",\"dob\":\"1990-01-01\",\"role\":\"DELEGATE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.TEXT_PLAIN).content("name=x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void oversizedBodyIs413() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(20_000) + "\",\"dob\":\"1990-01-01\"}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void duplicateKeysAndControlCharsAre400() throws Exception {
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"name\":\"b\",\"dob\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/validation-probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\\u0000\",\"dob\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parameterPollutionIs400() throws Exception {
        mvc.perform(get("/api/validation-probe?division=a&division=b")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/validation-probe?division=Melbourne")).andExpect(status().isOk());
    }
}
