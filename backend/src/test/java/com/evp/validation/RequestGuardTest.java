package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestGuardTest {
    private final RequestGuard.Limits L = RequestGuard.Limits.defaults();

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private String code(String json) {
        RequestGuard.Violation v = RequestGuard.checkJson(b(json), L);
        return v == null ? null : v.code();
    }

    @Test
    void validBodiesPass() {
        assertNull(code("{\"name\":\"Jane\",\"dob\":\"1990-01-01\",\"n\":[1,2,3],\"x\":{\"y\":null}}"));
        assertNull(code("[]"));
    }

    @Test
    void malformedJsonRejected() {
        assertEquals("malformed_json", code("{bad json"));
        assertEquals("malformed_json", code("{\"a\":"));
        assertEquals("empty_body", RequestGuard.checkJson(new byte[0], L).code());
    }

    @Test
    void duplicateKeysRejected() {
        assertEquals("duplicate_key", code("{\"role\":\"VOTER\",\"role\":\"DELEGATE\"}"));
    }

    @Test
    void trailingContentRejected() {
        assertEquals("trailing_content", code("{\"a\":1}{\"b\":2}"));
        assertEquals("trailing_content", code("{\"a\":1} 7"));
    }

    @Test
    void deepNestingRejected() {
        String deep = "{\"a\":".repeat(10) + "1" + "}".repeat(10);
        assertEquals("too_deeply_nested", code(deep));
        assertEquals("too_deeply_nested", code("[".repeat(50) + "]".repeat(50)));
    }

    @Test
    void controlCharactersRejectedEvenWhenEscaped() {
        assertEquals("control_characters", code("{\"name\":\"Jane\\u0000Citizen\"}"));
        assertEquals("control_characters", code("{\"name\":\"a\\r\\nb\"}"));
        assertEquals("invalid_field_name", code("{\"na\\u0000me\":\"x\"}"));
    }

    @Test
    void oversizedStringsAndNamesRejected() {
        assertEquals("string_too_long", code("{\"a\":\"" + "x".repeat(2049) + "\"}"));
        assertEquals("invalid_field_name", code("{\"" + "k".repeat(65) + "\":1}"));
        assertNull(code("{\"a\":\"" + "x".repeat(2048) + "\"}"));
    }

    @Test
    void nonUtf8EncodingsRejected() {
        byte[] utf16 = "{\"a\":1}".getBytes(StandardCharsets.UTF_16LE);
        assertEquals("unsupported_encoding", RequestGuard.checkJson(utf16, L).code());
    }

    @Test
    void contentTypeMustBeJson() {
        assertNull(RequestGuard.checkContentType("application/json"));
        assertNull(RequestGuard.checkContentType("application/json; charset=UTF-8"));
        assertEquals(415, RequestGuard.checkContentType("text/plain").status());
        assertEquals(415, RequestGuard.checkContentType("application/x-www-form-urlencoded").status());
        assertEquals(415, RequestGuard.checkContentType("application/xml").status());
        assertEquals(415, RequestGuard.checkContentType("application/json; charset=utf-16").status());
        assertEquals(415, RequestGuard.checkContentType(null).status());
    }

    @Test
    void queryParametersAreChecked() {
        assertNull(RequestGuard.checkQuery(Map.of("division", new String[] {"Melbourne"}), L));
        assertEquals("duplicate_parameter", RequestGuard.checkQuery(Map.of("division", new String[] {"a", "b"}), L).code());
        assertEquals("invalid_parameter", RequestGuard.checkQuery(Map.of("division", new String[] {"a\u0000"}), L).code());
        assertEquals("invalid_parameter", RequestGuard.checkQuery(Map.of("d", new String[] {"x".repeat(257)}), L).code());
        Map<String, String[]> many = new LinkedHashMap<>();
        for (int i = 0; i < 21; i++) {
            many.put("p" + i, new String[] {"1"});
        }
        assertEquals("too_many_parameters", RequestGuard.checkQuery(many, L).code());
        assertNull(RequestGuard.checkQuery(null, L));
    }

    @Test
    void violationsNeverContainInput() {
        RequestGuard.Violation v = RequestGuard.checkJson(b("{\"name\":\"<script>alert(1)</script>\u0001\"}"), L);
        assertNotNull(v);
        assertEquals(false, v.code().contains("script"));
    }
}
