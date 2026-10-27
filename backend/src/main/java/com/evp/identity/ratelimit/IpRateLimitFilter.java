package com.evp.identity.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/** Created only inside Spring Security, not registered a second time as a servlet filter. */
public class IpRateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    public IpRateLimitFilter(RequestRateLimiter limiter) { this.limiter = limiter; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        RateLimitPolicy policy = RateLimitPolicy.forRequest(request);
        if (policy != null) {
            // Never trust client-supplied Forwarded/X-Forwarded-For. Deployment must preserve peer IP.
            long retry = limiter.acquire(List.of(
                new RequestRateLimiter.Budget("ip:" + policy.path() + ":" + request.getRemoteAddr(), policy.perIp()),
                new RequestRateLimiter.Budget("endpoint:" + policy.path(), policy.total())));
            if (retry > 0) { reject(response, retry); return; }
        }
        chain.doFilter(request, response);
    }
    static void reject(HttpServletResponse response, long retry) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"RATE_LIMITED\"}");
    }
}
