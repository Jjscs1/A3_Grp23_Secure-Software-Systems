package com.evp.changerequest;

/** SEC-05: a proposer tried to approve their own change. Mapped to HTTP 403. */
public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException() {
        super("A proposer cannot approve their own change");
    }
}
