package com.evp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsoPastDateValidator implements ConstraintValidator<IsoPastDate, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || InputRules.isIsoPastDate(value);
    }
}
