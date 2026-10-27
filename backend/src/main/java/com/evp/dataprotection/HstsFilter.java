package com.evp.dataprotection;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds HSTS to every secure response and redirects plain HTTP to the configured canonical https host. The target
 * host comes from configuration, never from the Host header, so it cannot be turned into an open redirect.
 * Behind a TLS-terminating proxy set {@code trustForwardedProto=true} only if the proxy overwrites X-Forwarded-Proto.
 */
public class HstsFilter implements Filter {
    public static final String HSTS_VALUE = "max-age=63072000; includeSubDomains; preload";

    private final String canonicalHost;
    private final boolean trustForwardedProto;

    public HstsFilter(String canonicalHost, boolean trustForwardedProto) {
        if (canonicalHost == null || !canonicalHost.matches("[A-Za-z0-9.-]+(:[0-9]{1,5})?")) {
            throw new IllegalArgumentException("canonical host must be a plain host[:port]");
        }
        this.canonicalHost = canonicalHost;
        this.trustForwardedProto = trustForwardedProto;
    }

    private boolean secure(HttpServletRequest r) {
        if (r.isSecure()) {
            return true;
        }
        return trustForwardedProto && "https".equalsIgnoreCase(r.getHeader("X-Forwarded-Proto"));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest rq = (HttpServletRequest) req;
        HttpServletResponse rs = (HttpServletResponse) res;
        if (!secure(rq)) {
            String uri = rq.getRequestURI();
            String q = rq.getQueryString();
            // path is re-encoded by the container; refuse anything that is not a plain absolute path
            String path = uri != null && uri.startsWith("/") && !uri.startsWith("//") && !uri.contains("\\") ? uri : "/";
            rs.setStatus(308);
            rs.setHeader("Location", "https://" + canonicalHost + path + (q == null || q.contains("\r") || q.contains("\n") ? "" : "?" + q));
            return;
        }
        rs.setHeader("Strict-Transport-Security", HSTS_VALUE);
        chain.doFilter(req, res);
    }
}
