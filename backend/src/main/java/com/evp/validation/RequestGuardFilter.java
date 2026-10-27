package com.evp.validation;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * SEC-06: runs BEFORE Spring Security and any controller, for every /api/** request.
 * Rejects oversized, wrongly typed, malformed, duplicated or control-character input with a
 * generic JSON error. It never logs or echoes the request body or parameter values.
 * Spring Boot registers any Filter bean automatically, so SecurityConfig needs no change.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestGuardFilter implements Filter {

    private final RequestGuard.Limits limits;

    public RequestGuardFilter(@Value("${evp.validation.max-body-bytes:16384}") int maxBodyBytes,
                              @Value("${evp.validation.max-json-depth:6}") int maxJsonDepth,
                              @Value("${evp.validation.max-string-length:2048}") int maxStringLength) {
        RequestGuard.Limits d = RequestGuard.Limits.defaults();
        this.limits = new RequestGuard.Limits(maxBodyBytes, maxJsonDepth, maxStringLength,
                d.maxFieldNameLength(), d.maxParams(), d.maxParamLength());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        long declared = req.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(req.getHeader("Transfer-Encoding"));
        boolean hasBody = RequestGuard.methodMayHaveBody(req.getMethod()) && (declared > 0 || chunked);

        if (hasBody) {
            if (declared > limits.maxBodyBytes()) {
                reject(res, new RequestGuard.Violation(413, "payload_too_large"));
                return;
            }
            RequestGuard.Violation ct = RequestGuard.checkContentType(req.getContentType());
            if (ct != null) {
                reject(res, ct);
                return;
            }
        }

        RequestGuard.Violation q = RequestGuard.checkQuery(req.getParameterMap(), limits);
        if (q != null) {
            reject(res, q);
            return;
        }

        if (!hasBody) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = readBounded(req.getInputStream(), limits.maxBodyBytes());
        if (body == null) {
            reject(res, new RequestGuard.Violation(413, "payload_too_large"));
            return;
        }
        RequestGuard.Violation j = RequestGuard.checkJson(body, limits);
        if (j != null) {
            reject(res, j);
            return;
        }
        chain.doFilter(new CachedBodyRequest(req, body), response);
    }

    /** Reads at most max bytes; returns null if the body is larger (even without Content-Length). */
    private static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > max) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void reject(HttpServletResponse res, RequestGuard.Violation v) throws IOException {
        res.setStatus(v.status());
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        // code values are fixed constants from RequestGuard, never user input
        res.getWriter().write("{\"error\":\"Invalid request\",\"code\":\"" + v.code() + "\"}");
    }
}
