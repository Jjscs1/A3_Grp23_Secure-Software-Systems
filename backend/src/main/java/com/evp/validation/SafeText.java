package com.evp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allow-list check for free-text fields. Put it on request DTO fields (use with {@code @Valid}):
 * <pre>
 *   record Request(@NotNull @SafeText(kind = TextKind.NAME, max = 100) String name) { }
 * </pre>
 * null is treated as valid, so combine with {@code @NotNull} where the field is required.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {
    String message() default "invalid text";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    InputRules.TextKind kind() default InputRules.TextKind.NAME;
    int min() default 1;
    int max() default 100;
}
