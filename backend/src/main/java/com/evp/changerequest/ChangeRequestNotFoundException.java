package com.evp.changerequest;

/** Mapped to HTTP 404. */
public class ChangeRequestNotFoundException extends RuntimeException {
    public ChangeRequestNotFoundException() {
        super("Change request not found");
    }
}
