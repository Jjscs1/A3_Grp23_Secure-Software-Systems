package com.evp.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void meRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsMalformedToken() throws Exception {
        mvc.perform(get("/api/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsAuthenticatedIdentity() throws Exception {
        // Simulates an already authenticated JWT principal.
        // This does not test JWT signature validation.
        mvc.perform(get("/api/me")
                .with(jwt().jwt(token -> token
                    .subject("test-user-id")
                    .claim("preferred_username", "voter-test"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value("test-user-id"))
            .andExpect(jsonPath("$.username").value("voter-test"));
    }

    @Test
    void unapprovedMethodIsDeniedEvenWhenAuthenticated() throws Exception {
        mvc.perform(post("/api/me").with(jwt()))
            .andExpect(status().isForbidden());
    }
}