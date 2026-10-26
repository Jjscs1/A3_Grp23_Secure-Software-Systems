package com.evp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.evp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** SEC-07 acceptance criteria for the backend side (the front end has its own tests). */
class SecurityHeadersApiTest extends ApiTestBase {

    private MockHttpServletResponse anyResponse() throws Exception {
        return mvc.perform(get("/api/change-requests").with(user("alice", "DELEGATE"))).andReturn().getResponse();
    }

    @Test
    @DisplayName("SEC-07: CSP header has no unsafe-inline or unsafe-eval")
    void cspIsStrict() throws Exception {
        MockHttpServletResponse r = anyResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getHeader("Content-Security-Policy")).isNotNull()
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("object-src 'none'")
                .contains("frame-ancestors 'none'")
                .doesNotContain("unsafe-inline")
                .doesNotContain("unsafe-eval");
    }

    @Test
    @DisplayName("SEC-07: security headers are present on every response")
    void otherSecurityHeaders() throws Exception {
        MockHttpServletResponse r = anyResponse();
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(r.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(r.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("SEC-07: headers are also present on error responses")
    void headersOnErrors() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/api/change-requests")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getHeader("Content-Security-Policy")).isNotNull();
    }
}
