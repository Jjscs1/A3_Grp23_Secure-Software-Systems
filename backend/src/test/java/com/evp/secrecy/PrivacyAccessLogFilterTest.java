package com.evp.secrecy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrivacyAccessLogFilterTest {
    private static final String MARKER = "SECRET-BALLOT-MARKER-9f3a";

    private final List<String> logs = new ArrayList<>();
    private final PrivacyAccessLogFilter filter = new PrivacyAccessLogFilter(logs::add);

    @Test
    void logsOnlyMethodPathStatusAndDuration() throws Exception {
        ServletFakes.Response res = new ServletFakes.Response();
        filter.doFilter(ServletFakes.request("POST", "/api/ballots",
                Map.of("X-Forwarded-For", "203.0.113.7", "Authorization", "Bearer " + MARKER), "203.0.113.7"),
                res.proxy, (rq, rs) -> res.status = 201);
        assertEquals(1, logs.size());
        String line = logs.get(0);
        assertTrue(line.matches("\\{\"method\":\"POST\",\"path\":\"/api/ballots\",\"status\":201,\"ms\":\\d+}"), line);
        assertFalse(line.contains("203.0.113.7"));
        assertFalse(line.contains(MARKER));
    }

    @Test
    void neverReadsBodyHeadersParametersOrQueryString() throws Exception {
        // would throw AssertionError from ServletFakes if the filter touched them
        ServletFakes.Response res = new ServletFakes.Response();
        filter.doFilter(ServletFakes.request("POST", "/api/ballots", Map.of(), "10.0.0.1"), res.proxy, (rq, rs) -> { });
        assertEquals(1, logs.size());
    }

    @Test
    void failuresLogTheExceptionClassOnly_notItsMessage() throws Exception {
        ServletFakes.Response res = new ServletFakes.Response();
        filter.doFilter(ServletFakes.request("POST", "/api/ballots", Map.of(), "10.0.0.1"), res.proxy,
                (rq, rs) -> { throw new IllegalStateException("JSON parse error near " + MARKER); });
        String all = String.join("\n", logs);
        assertFalse(all.contains(MARKER));
        assertTrue(all.contains("IllegalStateException"));
        assertEquals(500, res.status);
        assertFalse(res.out.toString().contains(MARKER));
        assertTrue(res.out.toString().contains("Internal error"));
    }

    @Test
    void checkedExceptionsAreHandledTheSameWay() throws Exception {
        ServletFakes.Response res = new ServletFakes.Response();
        filter.doFilter(ServletFakes.request("GET", "/api/x", Map.of(), "10.0.0.1"), res.proxy,
                (rq, rs) -> { throw new ServletException(MARKER); });
        assertFalse(String.join("", logs).contains(MARKER));
        assertEquals(500, res.status);
        filter.doFilter(ServletFakes.request("GET", "/api/x", Map.of(), "10.0.0.1"), res.proxy,
                (rq, rs) -> { throw new IOException(MARKER); });
        assertFalse(String.join("", logs).contains(MARKER));
    }

    @Test
    void pathIsSanitisedSoTheLogLineCannotBeForged() throws Exception {
        ServletFakes.Response res = new ServletFakes.Response();
        filter.doFilter(ServletFakes.request("GET", "/api/x\",\"status\":200,\"y\":\"\n", Map.of(), "10.0.0.1"),
                res.proxy, (rq, rs) -> { });
        String line = logs.get(0);
        assertFalse(line.contains("\n"));
        assertEquals(1, line.split("\"status\":", -1).length - 1);
    }
}
