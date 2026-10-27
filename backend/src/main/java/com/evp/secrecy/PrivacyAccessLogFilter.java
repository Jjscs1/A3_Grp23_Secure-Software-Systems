package com.evp.secrecy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * SEC-13: the ONLY request log for /api/**. It records method, path (no query string), status and duration.
 * It never touches the body, headers, parameters, IP or user, and unexpected errors are logged by class
 * name only (exception messages such as JSON parse errors can quote the request body).
 */
public final class PrivacyAccessLogFilter implements Filter {
    private final Consumer<String> sink;

    public PrivacyAccessLogFilter(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        long t0 = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // class name only: the message could contain request content
            sink.accept("{\"event\":\"error\",\"type\":\"" + e.getClass().getSimpleName()
                    + "\",\"method\":\"" + safe(req.getMethod()) + "\",\"path\":\"" + safe(req.getRequestURI()) + "\"}");
            if (!res.isCommitted()) {
                res.reset();
                res.setStatus(500);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Internal error\"}");
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        sink.accept("{\"method\":\"" + safe(req.getMethod()) + "\",\"path\":\"" + safe(req.getRequestURI())
                + "\",\"status\":" + res.getStatus() + ",\"ms\":" + ms + "}");
    }

    /** getRequestURI has no query string; strip anything that could break the JSON line. */
    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(Math.min(s.length(), 200));
        for (int i = 0; i < s.length() && b.length() < 200; i++) {
            char c = s.charAt(i);
            b.append(c < 0x20 || c == '"' || c == '\\' || c > 0x7e ? '_' : c);
        }
        return b.toString();
    }
}
