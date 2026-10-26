package com.evp.token;

import com.evp.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SEC-11: forged tokens are 403, reused tokens are 409. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TokenExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    ResponseEntity<ApiError> invalidToken() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("INVALID_TOKEN"));
    }

    @ExceptionHandler(DuplicateTokenException.class)
    ResponseEntity<ApiError> duplicateToken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("TOKEN_ALREADY_USED"));
    }
}
