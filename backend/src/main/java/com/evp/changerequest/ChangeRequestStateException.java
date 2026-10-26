package com.evp.changerequest;

/** SEC-05: an action was attempted on a change that is no longer pending. Mapped to HTTP 409. */
public class ChangeRequestStateException extends RuntimeException {
    public ChangeRequestStateException(String message) {
        super(message);
    }
}
