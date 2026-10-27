package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RequestGuardFilterTest {
    private final RequestGuardFilter filter = new RequestGuardFilter(1024, 6, 2048);

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private record Result(boolean passed, int status, String body, ServletRequest forwarded) { }

    private Result run(String method, String uri, String ct, byte[] body, Map<String, String[]> params,
                       Map<String, String> headers) throws Exception {
        HttpServletRequest req = Fakes.request(method, uri, ct, body, params, headers);
        Fakes.Response res = new Fakes.Response();
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(req, res.proxy, (rq, rs) -> seen.set(rq));
        return new Result(seen.get() != null, res.status, res.out.toString(), seen.get());
    }

    private Result post(String ct, String json) throws Exception {
        return run("POST", "/api/tokens", ct, b(json), Map.of(), Map.of());
    }

    @Test
    void validJsonPassesAndBodyCanBeReadAgain() throws Exception {
        byte[] body = b("{\"name\":\"Jane\"}");
        Result r = run("POST", "/api/tokens", "application/json", body, Map.of(), Map.of());
        assertTrue(r.passed());
        assertArrayEquals(body, r.forwarded().getInputStream().readAllBytes());
        assertEquals("{\"name\":\"Jane\"}", r.forwarded().getReader().readLine());
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        Result r = post("text/plain", "name=x");
        assertFalse(r.passed());
        assertEquals(415, r.status());
        assertTrue(r.body().contains("unsupported_media_type"));
    }

    @Test
    void oversizedBodyIs413() throws Exception {
        Result r = post("application/json", "{\"a\":\"" + "x".repeat(2000) + "\"}");
        assertFalse(r.passed());
        assertEquals(413, r.status());
    }

    @Test
    void oversizedChunkedBodyWithoutContentLengthIs413() throws Exception {
        byte[] big = b("{\"a\":\"" + "x".repeat(2000) + "\"}");
        HttpServletRequest req = (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getMethod" -> "POST";
                    case "getRequestURI" -> "/api/ballots";
                    case "getContentType" -> "application/json";
                    case "getContentLengthLong" -> -1L;
                    case "getHeader" -> "Transfer-Encoding".equals(a[0]) ? "chunked" : null;
                    case "getParameterMap" -> Map.of();
                    case "getInputStream" -> Fakes.stream(big);
                    default -> null;
                });
        Fakes.Response res = new Fakes.Response();
        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(req, res.proxy, (rq, rs) -> seen.set(rq));
        assertEquals(413, res.status);
        assertEquals(null, seen.get());
    }

    @Test
    void malformedDuplicateAndControlCharBodiesAre400() throws Exception {
        assertEquals(400, post("application/json", "{bad").status());
        assertEquals(400, post("application/json", "{\"a\":1,\"a\":2}").status());
        assertEquals(400, post("application/json", "{\"a\":\"x\\u0000\"}").status());
        assertEquals(400, post("application/json", "{\"a\":1}{\"b\":2}").status());
    }

    @Test
    void errorBodyNeverEchoesInput() throws Exception {
        Result r = post("application/json", "{\"name\":\"<script>alert(1)</script>\\u0000\"}");
        assertEquals(400, r.status());
        assertFalse(r.body().contains("script"));
        assertTrue(r.body().startsWith("{\"error\":\"Invalid request\""));
    }

    @Test
    void duplicateQueryParametersAre400() throws Exception {
        Result r = run("GET", "/api/candidates", null, null, Map.of("division", new String[] {"a", "b"}), Map.of());
        assertEquals(400, r.status());
        assertTrue(r.body().contains("duplicate_parameter"));
    }

    @Test
    void cleanGetPasses() throws Exception {
        assertTrue(run("GET", "/api/candidates", null, null, Map.of("division", new String[] {"Melbourne"}), Map.of()).passed());
    }

    @Test
    void nonApiPathsAreNotTouched() throws Exception {
        Result r = run("POST", "/index.html", "text/plain", b("x"), Map.of("a", new String[] {"1", "2"}), Map.of());
        assertTrue(r.passed());
    }

    @Test
    void bodylessPostPasses() throws Exception {
        assertTrue(run("POST", "/api/auth/logout", null, null, Map.of(), Map.of()).passed());
    }
}
