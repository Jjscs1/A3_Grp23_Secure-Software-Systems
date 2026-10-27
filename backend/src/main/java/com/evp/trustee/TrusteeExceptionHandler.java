package com.evp.trustee;
import com.evp.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrusteeExceptionHandler {

    @ExceptionHandler(PollStillOpenException.class)
    ResponseEntity<ApiError> pollStillOpen(PollStillOpenException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(new ApiError("POLL_STILL_OPEN", e.getMessage()));
    }

    @ExceptionHandler(NotEnoughSharesException.class)
    ResponseEntity<ApiError> notEnoughShares(NotEnoughSharesException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("NOT_ENOUGH_SHARES", e.getMessage()));
    }

    @ExceptionHandler(ShareException.class)
    ResponseEntity<ApiError> invalidShares(ShareException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("INVALID_SHARES", e.getMessage()));
    }

    @ExceptionHandler(KeyCeremonyException.class)
    ResponseEntity<ApiError> keyCeremony(KeyCeremonyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("KEY_CEREMONY", e.getMessage()));
    }
}