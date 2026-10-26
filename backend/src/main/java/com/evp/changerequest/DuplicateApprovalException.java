package com.evp.changerequest;

/** SEC-05: the same delegate tried to approve twice. Mapped to HTTP 409. */
public class DuplicateApprovalException extends RuntimeException {
    public DuplicateApprovalException() {
        super("This delegate has already approved the change");
    }
}
