package com.evp.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Strict deserialisation rules (testable without Spring). */
public final class StrictJson {
    private StrictJson() { }

    @SuppressWarnings("deprecation")
    public static void apply(ObjectMapper m) {
        m.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);     // blocks mass assignment
        m.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);        // {...}{...}
        m.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        m.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        m.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);              // "5" is not accepted for an int
        m.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
}
