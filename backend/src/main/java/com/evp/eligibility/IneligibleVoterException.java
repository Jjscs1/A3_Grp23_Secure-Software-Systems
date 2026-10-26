package com.evp.eligibility;

/** SEC-10: thrown when a ballot must not be issued. Mapped to HTTP 403 by ApiExceptionHandler. */
public class IneligibleVoterException extends RuntimeException {

    private final IneligibilityReason reason;

    public IneligibleVoterException(IneligibilityReason reason) {
        super("Voter is not eligible to be issued a ballot: " + reason);
        this.reason = reason;
    }

    public IneligibilityReason getReason() {
        return reason;
    }
}
