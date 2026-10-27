package com.evp.secrecy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NetworkMetadataStripFilterTest {

    private HttpServletRequest seenDownstream(Map<String, String> headers) throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        HttpServletRequest in = ServletFakes.request("POST", "/api/ballots", headers, "203.0.113.7");
        new NetworkMetadataStripFilter().doFilter(in, new ServletFakes.Response().proxy,
                (rq, rs) -> seen.set((HttpServletRequest) rq));
        return seen.get();
    }

    @Test
    void ipAddressIsGoneForEverythingDownstream() throws Exception {
        HttpServletRequest r = seenDownstream(Map.of("Content-Type", "application/json"));
        assertEquals("", r.getRemoteAddr());
        assertEquals("", r.getRemoteHost());
        assertEquals(0, r.getRemotePort());
    }

    @Test
    void proxyAndClientIdentifyingHeadersAreRemoved() throws Exception {
        HttpServletRequest r = seenDownstream(Map.of(
                "X-Forwarded-For", "198.51.100.9", "X-Real-IP", "198.51.100.9", "Forwarded", "for=198.51.100.9",
                "Via", "1.1 proxy", "CF-Connecting-IP", "198.51.100.9", "True-Client-IP", "198.51.100.9",
                "User-Agent", "Mozilla/5.0", "Content-Type", "application/json"));
        for (String h : List.of("x-forwarded-for", "X-REAL-IP", "forwarded", "via", "cf-connecting-ip",
                "true-client-ip", "User-Agent")) {
            assertNull(r.getHeader(h), h);
            assertFalse(r.getHeaders(h).hasMoreElements(), h);
        }
        List<String> names = Collections.list(r.getHeaderNames()).stream().map(String::toLowerCase).toList();
        assertEquals(List.of("content-type"), names);
    }

    @Test
    void otherHeadersStillWork() throws Exception {
        HttpServletRequest r = seenDownstream(Map.of("Content-Type", "application/json", "Authorization", "Bearer x"));
        assertEquals("application/json", r.getHeader("content-type"));
        assertEquals("Bearer x", r.getHeader("Authorization"));
    }

    @Test
    void bodyIsNeverReadByTheFilter() throws Exception {
        // ServletFakes throws if the body, parameters or query string are touched
        assertTrue(seenDownstream(Map.of()) != null);
    }
}
