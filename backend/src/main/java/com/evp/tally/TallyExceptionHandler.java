package com.evp.tally;
import com.evp.web.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TallyExceptionHandler {

    @ExceptionHandler(TallyException.class)
    ResponseEntity<ApiError> tally(TallyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("TALLY_REFUSED", e.getMessage()));
    }

    @ExceptionHandler(TallyNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND"));
    }
}