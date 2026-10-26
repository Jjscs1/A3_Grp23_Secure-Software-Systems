package com.evp.changerequest;

/**
 * SEC-05: carries out an approved change. The dual control workflow is the only caller,
 * so a change can only reach the ballot through it.
 */
public interface ChangeRequestApplier {

    boolean supports(ChangeRequestType type);

    /** Called at proposal time, so a malformed payload is rejected before anyone approves it. */
    default void validate(String payload) {
    }

    void apply(ChangeRequest request);
}
