package com.evp.identity.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookie is an additional binding; it NEVER authenticates without a valid DPoP token. */
public class BoundSessionFilter extends OncePerRequestFilter {
    private final BoundSessionService sessions;
    private final String origin;
    public BoundSessionFilter(BoundSessionService sessions, String origin) {
        this.sessions = sessions; this.origin = origin;
    }
    public static String cookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (BoundSessionService.COOKIE.equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            // Extra CSRF defence: mutation requests must originate at the configured app origin.
            if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
                    && !"OPTIONS".equals(request.getMethod()) && !origin.equals(request.getHeader("Origin"))) {
                response.setStatus(403); return;
            }
            if (!("POST".equals(request.getMethod()) && "/api/session/start".equals(request.getRequestURI()))) {
                try { sessions.require(jwt, cookie(request)); }
                catch (SessionRejected e) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"SESSION_ENDED\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
