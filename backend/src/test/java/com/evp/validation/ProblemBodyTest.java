package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProblemBodyTest {
    @Test
    void containsFieldAndCodeOnly() {
        Map<String, Object> m = ProblemBody.of("validation_failed", List.of(new ProblemBody.Issue("name", "SafeText")));
        assertEquals("Invalid request", m.get("error"));
        assertEquals("validation_failed", m.get("code"));
        assertTrue(m.get("issues").toString().contains("name"));
        assertFalse(ProblemBody.of("malformed_body", null).containsKey("issues"));
    }
}
