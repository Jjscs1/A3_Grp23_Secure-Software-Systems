package com.evp.identity.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after authentication; untrusted usernames cannot exhaust another account's budget. */
public class AccountRateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    public AccountRateLimitFilter(RequestRateLimiter limiter) { this.limiter = limiter; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        RateLimitPolicy policy = RateLimitPolicy.forRequest(request);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (policy != null && policy.perAccount() > 0
                && auth instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            var jwt = token.getToken();
            String identity = String.valueOf(jwt.getIssuer()) + "\n" + jwt.getSubject();
            long retry = limiter.acquire(List.of(new RequestRateLimiter.Budget(
                "account:" + policy.path() + ":" + digest(identity), policy.perAccount())));
            if (retry > 0) { IpRateLimitFilter.reject(response, retry); return; }
        }
        chain.doFilter(request, response);
    }
    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
