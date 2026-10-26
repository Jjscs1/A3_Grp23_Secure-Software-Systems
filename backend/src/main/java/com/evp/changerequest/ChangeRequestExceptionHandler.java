package com.evp.changerequest;

import com.evp.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SEC-05: HTTP status for each way the four-eyes workflow can refuse an action. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChangeRequestExceptionHandler {

    @ExceptionHandler(SelfApprovalException.class)
    ResponseEntity<ApiError> selfApproval() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("SELF_APPROVAL_FORBIDDEN"));
    }

    @ExceptionHandler({DuplicateApprovalException.class, ChangeRequestStateException.class})
    ResponseEntity<ApiError> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("CONFLICT"));
    }

    @ExceptionHandler(ChangeRequestNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND"));
    }

    @ExceptionHandler(InvalidChangeRequestException.class)
    ResponseEntity<ApiError> invalid(InvalidChangeRequestException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("INVALID_CHANGE_REQUEST", e.getMessage()));
    }
}
