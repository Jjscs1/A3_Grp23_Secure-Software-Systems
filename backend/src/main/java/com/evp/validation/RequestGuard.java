package com.evp.validation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SEC-06: request-level checks that apply to EVERY /api endpoint, independent of the DTO.
 * Pure Java (Jackson streaming only) so it can be unit-tested without Spring.
 * Responses never echo the offending input: only a fixed code.
 */
public final class RequestGuard {

    public record Limits(int maxBodyBytes, int maxJsonDepth, int maxStringLength, int maxFieldNameLength,
                         int maxParams, int maxParamLength) {
        public static Limits defaults() {
            return new Limits(16 * 1024, 6, 2048, 64, 20, 256);
        }
    }

    public record Violation(int status, String code) { }

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private RequestGuard() { }

    public static boolean methodMayHaveBody(String method) {
        return BODY_METHODS.contains(method) || "DELETE".equals(method);
    }

    /** State-changing requests with a body must be UTF-8 JSON (blocks form/XML/multipart smuggling). */
    public static Violation checkContentType(String contentType) {
        if (contentType == null) {
            return new Violation(415, "unsupported_media_type");
        }
        String ct = contentType.toLowerCase(Locale.ROOT).replace(" ", "");
        boolean json = ct.equals("application/json") || ct.startsWith("application/json;");
        boolean charsetOk = !ct.contains("charset=") || ct.contains("charset=utf-8");
        return json && charsetOk ? null : new Violation(415, "unsupported_media_type");
    }

    /** No duplicate (HTTP parameter pollution), oversized, too many, or control-character parameters. */
    public static Violation checkQuery(Map<String, String[]> params, Limits l) {
        if (params == null) {
            return null;
        }
        if (params.size() > l.maxParams()) {
            return new Violation(400, "too_many_parameters");
        }
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            if (name.length() > l.maxParamLength() || InputRules.hasControlChars(name)) {
                return new Violation(400, "invalid_parameter");
            }
            if (values != null && values.length > 1) {
                return new Violation(400, "duplicate_parameter");
            }
            if (values != null) {
                for (String v : values) {
                    if (v.length() > l.maxParamLength() || InputRules.hasControlChars(v)) {
                        return new Violation(400, "invalid_parameter");
                    }
                }
            }
        }
        return null;
    }

    private static final JsonFactory STRICT = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /** Structural checks on a JSON body: one root value, depth, duplicate keys, string sizes, control chars. */
    public static Violation checkJson(byte[] body, Limits l) {
        if (body.length == 0) {
            return new Violation(400, "empty_body");
        }
        for (int i = 0; i < Math.min(4, body.length); i++) {
            if (body[i] == 0) { // UTF-16/32 or NUL-padded input: only UTF-8 is accepted
                return new Violation(400, "unsupported_encoding");
            }
        }
        try (JsonParser p = STRICT.createParser(body)) {
            int depth = 0;
            int roots = 0;
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                switch (t) {
                    case START_OBJECT, START_ARRAY -> {
                        if (++depth > l.maxJsonDepth()) {
                            return new Violation(400, "too_deeply_nested");
                        }
                    }
                    case END_OBJECT, END_ARRAY -> {
                        depth--;
                        if (depth == 0) {
                            roots++;
                        }
                    }
                    case FIELD_NAME -> {
                        String n = p.currentName();
                        if (n.length() > l.maxFieldNameLength() || InputRules.hasControlChars(n)) {
                            return new Violation(400, "invalid_field_name");
                        }
                    }
                    case VALUE_STRING -> {
                        String s = p.getText();
                        if (s.length() > l.maxStringLength()) {
                            return new Violation(400, "string_too_long");
                        }
                        if (InputRules.hasControlChars(s)) {
                            return new Violation(400, "control_characters");
                        }
                        if (depth == 0) {
                            roots++;
                        }
                    }
                    default -> {
                        if (depth == 0) {
                            roots++;
                        }
                    }
                }
                if (roots > 1) {
                    return new Violation(400, "trailing_content");
                }
            }
            return roots == 1 ? null : new Violation(400, "malformed_json");
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            return new Violation(400, m.contains("Duplicate field") ? "duplicate_key" : "malformed_json");
        }
    }
}
