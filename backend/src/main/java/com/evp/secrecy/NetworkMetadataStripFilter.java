package com.evp.secrecy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * SEC-13: on ballot routes nothing downstream (controllers, Spring Security, loggers, rate limiters) can see
 * the client's IP address or proxy headers. Register it first for /api/ballots/** and /api/ballotbox/**
 * (see SecrecyConfig). Rate limiting on these routes must therefore key on the signed-in user or token,
 * never on the IP.
 */
public final class NetworkMetadataStripFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new StrippedRequest((HttpServletRequest) request), response);
    }
}
