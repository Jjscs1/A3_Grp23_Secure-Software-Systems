package com.evp.eligibility;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-10 acceptance criteria, one test per checkbox on issue SEC-10. */
class EligibilityPolicyTest {

    private static final LocalDate ELECTION_DAY = LocalDate.of(2026, 5, 2);
    private static final LocalDate ADULT_DOB = LocalDate.of(1990, 1, 1);

    private static void check(boolean enrolled, boolean citizen, LocalDate dob, boolean hasVoted, String requested) {
        EligibilityPolicy.check(enrolled, citizen, dob, "MELBOURNE", hasVoted, requested, ELECTION_DAY);
    }

    @Test
    @DisplayName("Eligible voter is allowed")
    void eligibleVoterPasses() {
        assertThatCode(() -> check(true, true, ADULT_DOB, false, "MELBOURNE")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SEC-10: token issuance is refused for a voter under 18")
    void under18Refused() {
        LocalDate seventeen = ELECTION_DAY.minusYears(18).plusDays(1);
        assertThatThrownBy(() -> check(true, true, seventeen, false, "MELBOURNE"))
                .isInstanceOf(IneligibleVoterException.class)
                .extracting("reason").isEqualTo(IneligibilityReason.UNDER_18);
    }

    @Test
    @DisplayName("A voter who turns 18 on polling day is eligible")
    void eighteenthBirthdayOnPollingDayIsEligible() {
        assertThatCode(() -> check(true, true, ELECTION_DAY.minusYears(18), false, "MELBOURNE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SEC-10: token issuance is refused for a voter who is not enrolled")
    void notEnrolledRefused() {
        assertThatThrownBy(() -> check(false, true, ADULT_DOB, false, "MELBOURNE"))
                .extracting("reason").isEqualTo(IneligibilityReason.NOT_ENROLLED);
    }

    @Test
    @DisplayName("SEC-10: token issuance is refused for a voter in the wrong division")
    void wrongDivisionRefused() {
        assertThatThrownBy(() -> check(true, true, ADULT_DOB, false, "WILLS"))
                .extracting("reason").isEqualTo(IneligibilityReason.WRONG_DIVISION);
    }

    @Test
    @DisplayName("SEC-10: token issuance is refused for a voter who has already voted")
    void alreadyVotedRefused() {
        assertThatThrownBy(() -> check(true, true, ADULT_DOB, true, "MELBOURNE"))
                .extracting("reason").isEqualTo(IneligibilityReason.ALREADY_VOTED);
    }

    @Test
    @DisplayName("A non-citizen is refused")
    void nonCitizenRefused() {
        assertThatThrownBy(() -> check(true, false, ADULT_DOB, false, "MELBOURNE"))
                .extracting("reason").isEqualTo(IneligibilityReason.NOT_A_CITIZEN);
    }
}
