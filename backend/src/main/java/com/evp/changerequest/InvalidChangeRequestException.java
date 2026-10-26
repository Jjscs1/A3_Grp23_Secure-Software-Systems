package com.evp.changerequest;

/** SEC-05: a proposal is missing something it needs, such as a court order reference. Mapped to HTTP 422. */
public class InvalidChangeRequestException extends RuntimeException {
    public InvalidChangeRequestException(String message) {
        super(message);
    }
}
