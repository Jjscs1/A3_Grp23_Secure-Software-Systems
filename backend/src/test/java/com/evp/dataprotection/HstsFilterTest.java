package com.evp.dataprotection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HstsFilterTest {
    Fakes.Out run(HstsFilter f, boolean secure, String uri, String q, Map<String, String> h) throws Exception {
        Fakes.Out out = new Fakes.Out();
        f.doFilter(Fakes.request(secure, uri, q, h), Fakes.response(out), Fakes.chain(out));
        return out;
    }

    @Test
    void secureResponsesGetHstsAndContinue() throws Exception {
        Fakes.Out o = run(new HstsFilter("vote.example.gov.au", false), true, "/api/x", null, Map.of());
        assertEquals("max-age=63072000; includeSubDomains; preload", o.headers.get("Strict-Transport-Security"));
        assertTrue(o.chained);
    }

    @Test
    void plainHttpIsRedirectedToConfiguredHostOnly() throws Exception {
        Fakes.Out o = run(new HstsFilter("vote.example.gov.au", false), false, "/ballot", "a=1", Map.of("Host", "evil.com"));
        assertEquals(308, o.status);
        assertEquals("https://vote.example.gov.au/ballot?a=1", o.headers.get("Location"));
        assertFalse(o.chained);
        assertFalse(o.headers.containsKey("Strict-Transport-Security"));
    }

    @Test
    void protocolRelativeAndBackslashPathsCannotEscapeTheHost() throws Exception {
        for (String uri : new String[] {"//evil.com/x", "/\\evil.com", "evil.com"}) {
            Fakes.Out o = run(new HstsFilter("vote.example.gov.au", false), false, uri, null, Map.of());
            assertEquals("https://vote.example.gov.au/", o.headers.get("Location"), uri);
        }
    }

    @Test
    void forwardedProtoIgnoredUnlessTrusted() throws Exception {
        Map<String, String> h = Map.of("X-Forwarded-Proto", "https");
        assertEquals(308, run(new HstsFilter("a.b", false), false, "/", null, h).status);
        assertTrue(run(new HstsFilter("a.b", true), false, "/", null, h).chained);
    }

    @Test
    void rejectsBadCanonicalHost() {
        for (String bad : new String[] {"", "evil.com/path", "a b", "x@y.com", "a.b:99999999"}) {
            assertThrows(IllegalArgumentException.class, () -> new HstsFilter(bad, false), bad);
        }
    }
}
