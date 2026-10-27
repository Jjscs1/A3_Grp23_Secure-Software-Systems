package com.evp.crypto;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Security auto-config is excluded: this test is about the payload. Authorisation is SecurityConfig's job. */
@WebMvcTest(controllers = ElectionKeyController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import(ElectionKeyControllerTest.Config.class)
class ElectionKeyControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        EnvelopeService envelopeService() throws Exception {
            ElectionKeyring ring = KeyringLoader.ephemeral("k-test", 2048);
            return new EnvelopeService(AlgorithmRegistry.withDefaults(), () -> ring);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void publishesPublicKeyOnly() throws Exception {
        mvc.perform(get("/api/election/key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kid").value("k-test"))
                .andExpect(jsonPath("$.alg").value("RSA-OAEP-256+A256GCM"))
                .andExpect(jsonPath("$.publicKey").isString())
                .andExpect(jsonPath("$.fingerprint").isString())
                .andExpect(content().string(not(containsString("PRIVATE"))))
                .andExpect(content().string(not(containsString("privateKey"))));
    }
}
