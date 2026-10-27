package com.evp.identity.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/** Limits per rolling 60 seconds; these are local-demo defaults, not production sizing. */
record RateLimitPolicy(String path, int perIp, int perAccount, int total) {
    static RateLimitPolicy forRequest(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return switch (path) {
            case "/api/public/enrolment-check" -> new RateLimitPolicy(path, 20, 0, 1000);
            case "/api/ballots" -> new RateLimitPolicy(path, 30, 0, 1000);
            case "/api/tokens" -> new RateLimitPolicy(path, 30, 10, 1000);
            case "/api/session/start", "/api/session/prepare-cast" -> new RateLimitPolicy(path, 30, 10, 1000);
            default -> null;
        };
    }
}
