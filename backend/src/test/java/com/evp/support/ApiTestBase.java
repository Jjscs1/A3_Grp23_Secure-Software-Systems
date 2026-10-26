package com.evp.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.evp.voter.VoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared test setup: full Spring context, in-memory H2, MockMvc, recorded audit log.
 * Each test class also clears the tables its own feature owns in a @BeforeEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestBase.AuditTestConfig.class)
public abstract class ApiTestBase {

    @TestConfiguration
    public static class AuditTestConfig {
        @Bean
        @Primary
        public RecordingAuditLogger recordingAuditLogger() {
            return new RecordingAuditLogger();
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected RecordingAuditLogger audit;

    @Autowired
    protected VoterRepository voters;

    @BeforeEach
    void resetSharedState() {
        voters.deleteAll();
        audit.clear();
    }

    /** A logged-in user with the given OIDC subject and realm role. */
    protected static JwtRequestPostProcessor user(String subject, String role) {
        return jwt().jwt(j -> j.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
