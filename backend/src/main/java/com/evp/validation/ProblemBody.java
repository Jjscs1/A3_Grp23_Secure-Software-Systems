package com.evp.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Error body shape: field names and constraint codes only, never the rejected value. */
public final class ProblemBody {
    public record Issue(String field, String code) { }

    private ProblemBody() { }

    public static Map<String, Object> of(String code, List<Issue> issues) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", "Invalid request");
        m.put("code", code);
        if (issues != null && !issues.isEmpty()) {
            m.put("issues", issues);
        }
        return m;
    }
}
