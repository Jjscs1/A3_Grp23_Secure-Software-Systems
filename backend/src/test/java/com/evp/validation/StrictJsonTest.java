package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StrictJsonTest {
    public static class Req {
        public String name;
        public int age;
    }

    private static ObjectMapper strict() {
        ObjectMapper m = new ObjectMapper();
        StrictJson.apply(m);
        return m;
    }

    @Test
    void validPayloadParses() throws Exception {
        Req r = strict().readValue("{\"name\":\"Jane\",\"age\":30}", Req.class);
        assertEquals("Jane", r.name);
        assertEquals(30, r.age);
    }

    @Test
    void unknownFieldsAreRejected_massAssignment() {
        assertThrows(JsonProcessingException.class,
                () -> strict().readValue("{\"name\":\"Jane\",\"age\":30,\"role\":\"DELEGATE\"}", Req.class));
    }

    @Test
    void lenientMapperWouldHaveAcceptedIt() {
        ObjectMapper lenient = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        assertDoesNotThrow(() -> lenient.readValue("{\"name\":\"Jane\",\"age\":30,\"role\":\"DELEGATE\"}", Req.class));
    }

    @Test
    void trailingTokensRejected() {
        assertThrows(JsonProcessingException.class, () -> strict().readValue("{\"name\":\"a\",\"age\":1} {\"x\":1}", Req.class));
    }

    @Test
    void duplicateKeysRejected() {
        assertThrows(JsonProcessingException.class, () -> strict().readValue("{\"name\":\"a\",\"name\":\"b\",\"age\":1}", Req.class));
    }

    @Test
    void stringIsNotCoercedToNumber_andNullNotAcceptedForPrimitive() {
        assertThrows(JsonProcessingException.class, () -> strict().readValue("{\"name\":\"a\",\"age\":\"5\"}", Req.class));
        assertThrows(JsonProcessingException.class, () -> strict().readValue("{\"name\":\"a\",\"age\":null}", Req.class));
    }
}
