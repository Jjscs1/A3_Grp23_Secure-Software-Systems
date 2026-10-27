package com.evp.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * SEC-06: every validation failure becomes a generic 400/415 that lists field names and
 * constraint codes only. No stack trace, no rejected value, no framework message.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ValidationProblemAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<ProblemBody.Issue> issues = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ProblemBody.Issue(fe.getField(), String.valueOf(fe.getCode())))
                .toList();
        return ResponseEntity.status(400).body(ProblemBody.of("validation_failed", issues));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> violations(ConstraintViolationException e) {
        List<ProblemBody.Issue> issues = e.getConstraintViolations().stream()
                .map((ConstraintViolation<?> v) -> new ProblemBody.Issue(v.getPropertyPath().toString(),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
                .toList();
        return ResponseEntity.status(400).body(ProblemBody.of("validation_failed", issues));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400).body(ProblemBody.of("malformed_body", null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> mismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(400)
                .body(ProblemBody.of("validation_failed", List.of(new ProblemBody.Issue(e.getName(), "type_mismatch"))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> missing(MissingServletRequestParameterException e) {
        return ResponseEntity.status(400)
                .body(ProblemBody.of("validation_failed", List.of(new ProblemBody.Issue(e.getParameterName(), "missing"))));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(ProblemBody.of("unsupported_media_type", null));
    }
}
