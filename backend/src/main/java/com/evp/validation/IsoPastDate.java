package com.evp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A real calendar date, ISO format YYYY-MM-DD, from 1900 up to today (e.g. date of birth). */
@Documented
@Constraint(validatedBy = IsoPastDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoPastDate {
    String message() default "invalid date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
