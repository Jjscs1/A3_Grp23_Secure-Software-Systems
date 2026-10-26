package com.evp.changerequest;

/** SEC-05: kinds of change that need a proposer and a different approver. */
public enum ChangeRequestType {
    /** Adding or editing a candidate's details on the ballot. */
    CANDIDATE_DETAILS,
    /** Changing the order of Senate groups or candidates on the ballot paper. */
    GROUP_ORDERING,
    /** Excluding a candidate for a recount ordered by the Court of Disputed Returns. */
    RECOUNT_EXCLUSION,
    /** An enrolment change affecting more than 50 records at once. */
    BULK_ENROLMENT_CHANGE
}
