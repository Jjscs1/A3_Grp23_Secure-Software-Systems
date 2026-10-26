package com.evp.token;

/** SEC-11: the token's signature did not verify against the election key. Mapped to HTTP 403. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Voting token is not valid");
    }
}
