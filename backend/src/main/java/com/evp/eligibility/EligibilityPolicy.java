package com.evp.eligibility;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * SEC-10: server side eligibility rules, kept free of Spring and JPA so the rules
 * can be unit tested on their own.
 *
 * A person may be issued a ballot only if they are enrolled, an Australian citizen,
 * 18 or over on election day, enrolled in the division whose ballot they are asking
 * for, and have not already voted. The client never decides any of this.
 */
public final class EligibilityPolicy {

    public static final int MINIMUM_VOTING_AGE = 18;

    private EligibilityPolicy() {
    }

    /**
     * @param enrolled          true if the voter is on the electoral roll
     * @param citizen           true if the voter is an Australian citizen
     * @param dateOfBirth       voter's date of birth
     * @param enrolledDivision  division the voter is enrolled in
     * @param hasVoted          true if a ballot token was already issued to this voter
     * @param requestedDivision division of the ballot being requested
     * @param electionDay       the date age is measured against
     * @throws IneligibleVoterException with the first rule that fails
     */
    public static void check(boolean enrolled,
                             boolean citizen,
                             LocalDate dateOfBirth,
                             String enrolledDivision,
                             boolean hasVoted,
                             String requestedDivision,
                             LocalDate electionDay) {
        Objects.requireNonNull(electionDay, "electionDay");

        if (!enrolled) {
            throw new IneligibleVoterException(IneligibilityReason.NOT_ENROLLED);
        }
        if (!citizen) {
            throw new IneligibleVoterException(IneligibilityReason.NOT_A_CITIZEN);
        }
        if (dateOfBirth == null
                || Period.between(dateOfBirth, electionDay).getYears() < MINIMUM_VOTING_AGE) {
            throw new IneligibleVoterException(IneligibilityReason.UNDER_18);
        }
        if (enrolledDivision == null || !enrolledDivision.equals(requestedDivision)) {
            throw new IneligibleVoterException(IneligibilityReason.WRONG_DIVISION);
        }
        if (hasVoted) {
            throw new IneligibleVoterException(IneligibilityReason.ALREADY_VOTED);
        }
    }
}
