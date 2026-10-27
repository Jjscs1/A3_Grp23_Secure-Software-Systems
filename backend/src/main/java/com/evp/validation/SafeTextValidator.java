package com.evp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeTextValidator implements ConstraintValidator<SafeText, String> {
    private InputRules.TextKind kind;
    private int min;
    private int max;

    @Override
    public void initialize(SafeText a) {
        this.kind = a.kind();
        this.min = a.min();
        this.max = a.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || InputRules.check(kind, value, min, max);
    }
}
