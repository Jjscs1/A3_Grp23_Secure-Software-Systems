package com.evp.changerequest;

/** SEC-05: lifecycle of a change request. Only APPLIED changes have taken effect. */
public enum ChangeRequestStatus {
    PENDING,
    APPLIED,
    REJECTED
}
