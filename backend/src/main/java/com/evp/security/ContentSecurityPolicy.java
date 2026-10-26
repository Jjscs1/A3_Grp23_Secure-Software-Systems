package com.evp.security;

/**
 * SEC-07: the Content Security Policy sent on every response.
 *
 * No 'unsafe-inline' and no 'unsafe-eval', so injected script cannot run even if
 * output encoding is ever missed somewhere. Trusted Types makes the browser refuse
 * raw HTML string sinks such as innerHTML.
 *
 * If the React build is served by nginx rather than Spring, nginx must send this same
 * header, because the HTML page is what the browser applies CSP to.
 */
public final class ContentSecurityPolicy {

    public static final String POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "require-trusted-types-for 'script'");

    private ContentSecurityPolicy() {
    }
}
