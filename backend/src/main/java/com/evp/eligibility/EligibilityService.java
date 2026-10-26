package com.evp.eligibility;

import com.evp.voter.Voter;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SEC-10: applies EligibilityPolicy to a voter record. Called by TokenIssuanceService
 * inside the same transaction that issues the token, so the check and the has-voted
 * flag cannot drift apart.
 */
@Service
public class EligibilityService {

    private final Clock clock;
    private final LocalDate configuredElectionDay;

    public EligibilityService(Clock clock, @Value("${evp.election.day:}") String electionDay) {
        this.clock = clock;
        this.configuredElectionDay = (electionDay == null || electionDay.isBlank())
                ? null
                : LocalDate.parse(electionDay);
    }

    public void check(Voter voter, String requestedDivision) {
        EligibilityPolicy.check(
                voter.isEnrolled(),
                voter.isCitizen(),
                voter.getDateOfBirth(),
                voter.getDivision(),
                voter.hasVoted(),
                requestedDivision,
                electionDay());
    }

    /** Age is measured on polling day; falls back to today if no election day is configured. */
    public LocalDate electionDay() {
        return configuredElectionDay != null ? configuredElectionDay : LocalDate.now(clock);
    }
}
