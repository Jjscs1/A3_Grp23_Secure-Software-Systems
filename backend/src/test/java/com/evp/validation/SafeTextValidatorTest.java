package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeTextValidatorTest {

    @SafeText(kind = InputRules.TextKind.NAME, min = 2, max = 20)
    private String name;

    private static SafeTextValidator validatorFor(String field) throws Exception {
        SafeText a = SafeTextValidatorTest.class.getDeclaredField(field).getAnnotation(SafeText.class);
        SafeTextValidator v = new SafeTextValidator();
        v.initialize(a);
        return v;
    }

    @Test
    void annotationParametersAreHonoured() throws Exception {
        SafeTextValidator v = validatorFor("name");
        assertTrue(v.isValid("Jane", null));
        assertTrue(v.isValid(null, null), "null is left to @NotNull");
        assertFalse(v.isValid("J", null));
        assertFalse(v.isValid("J".repeat(21), null));
        assertFalse(v.isValid("<script>", null));
    }

    @Test
    void isoPastDateValidator() {
        IsoPastDateValidator v = new IsoPastDateValidator();
        assertTrue(v.isValid("1990-01-01", null));
        assertTrue(v.isValid(null, null));
        assertFalse(v.isValid("2999-01-01", null));
        assertFalse(v.isValid("2020-02-30", null));
    }
}
