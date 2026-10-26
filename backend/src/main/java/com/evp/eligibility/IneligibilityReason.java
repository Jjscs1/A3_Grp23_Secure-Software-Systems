package com.evp.eligibility;

/** SEC-10: why a ballot (voting token) was refused. Checked in this order. */
public enum IneligibilityReason {
    NOT_ENROLLED,
    NOT_A_CITIZEN,
    UNDER_18,
    WRONG_DIVISION,
    ALREADY_VOTED
}
