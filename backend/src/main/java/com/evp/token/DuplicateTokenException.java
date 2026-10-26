package com.evp.token;

/** SEC-11: this voting token has already been used to cast a ballot. Mapped to HTTP 409. */
public class DuplicateTokenException extends RuntimeException {
    public DuplicateTokenException() {
        super("This voting token has already been used");
    }
}
