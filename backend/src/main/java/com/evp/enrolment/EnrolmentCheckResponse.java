package com.evp.enrolment;

/** SEC-08: the only answer the public check ever gives. Same shape whether found or not. */
public record EnrolmentCheckResponse(boolean enrolled) {
}
