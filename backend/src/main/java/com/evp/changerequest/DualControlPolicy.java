package com.evp.changerequest;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SEC-05: the four-eyes rules, kept free of Spring and JPA so they can be unit tested.
 *
 * Every change needs at least one approval from a delegate other than the proposer.
 * A recount exclusion needs two approvals from two different delegates, neither of
 * them the proposer (so three delegates are involved in total), plus a court order
 * reference. A bulk enrolment change only goes through this workflow when it affects
 * more than 50 records.
 */
public final class DualControlPolicy {

    public static final int BULK_ENROLMENT_THRESHOLD = 50;

    /** Letters, digits, spaces, slash, dot and hyphen; 3 to 64 characters. */
    private static final Pattern COURT_ORDER_REFERENCE =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 /.\\-]{2,63}$");

    private DualControlPolicy() {
    }

    public static int requiredApprovals(ChangeRequestType type) {
        Objects.requireNonNull(type, "type");
        return type == ChangeRequestType.RECOUNT_EXCLUSION ? 2 : 1;
    }

    /** Rejects a proposal before it is stored if it can never be valid. */
    public static void validateProposal(ChangeRequestType type, String courtOrderReference, Integer recordCount) {
        Objects.requireNonNull(type, "type");
        if (type == ChangeRequestType.RECOUNT_EXCLUSION
                && (courtOrderReference == null || !COURT_ORDER_REFERENCE.matcher(courtOrderReference).matches())) {
            throw new InvalidChangeRequestException("A recount exclusion requires a valid court order reference");
        }
        if (type == ChangeRequestType.BULK_ENROLMENT_CHANGE
                && (recordCount == null || recordCount <= BULK_ENROLMENT_THRESHOLD)) {
            throw new InvalidChangeRequestException(
                    "Only enrolment changes affecting more than " + BULK_ENROLMENT_THRESHOLD + " records use this workflow");
        }
    }

    /** Throws if {@code approver} may not approve this change. */
    public static void checkApprover(String proposer, String approver, Collection<String> existingApprovers) {
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(approver, "approver");
        if (proposer.equals(approver)) {
            throw new SelfApprovalException();
        }
        if (existingApprovers != null && existingApprovers.contains(approver)) {
            throw new DuplicateApprovalException();
        }
    }

    public static boolean isSatisfied(ChangeRequestType type, int distinctApprovals) {
        return distinctApprovals >= requiredApprovals(type);
    }
}
