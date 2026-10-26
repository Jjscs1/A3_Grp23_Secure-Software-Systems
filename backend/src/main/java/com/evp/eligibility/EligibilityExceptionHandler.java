package com.evp.eligibility;

import com.evp.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SEC-10: a refused ballot is HTTP 403 with the reason, which the voter's own screen explains. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EligibilityExceptionHandler {

    @ExceptionHandler(IneligibleVoterException.class)
    ResponseEntity<ApiError> ineligible(IneligibleVoterException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("NOT_ELIGIBLE", e.getReason().name()));
    }
}
