package com.evp.enrolment;

import com.evp.voter.Voter;
import com.evp.voter.VoterRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo data only. Runs when started with SPRING_PROFILES_ACTIVE=demo, and only if the
 * roll is empty. Each voter shows a different SEC-08 or SEC-10 outcome in the recording.
 *
 * The first argument of each seed() call is the login username. It must match a user in
 * the identity provider (SEC-01), because the backend reads the user from the
 * preferred_username claim (see SecurityConfig).
 */
@Component
@Profile("demo")
public class DemoVoterSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoVoterSeeder.class);

    private final VoterRepository voters;
    private final EnrolmentLookupKey lookupKey;

    public DemoVoterSeeder(VoterRepository voters, EnrolmentLookupKey lookupKey) {
        this.voters = voters;
        this.lookupKey = lookupKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (voters.count() > 0) {
            return;
        }
        seed("voter1", "Jane Citizen", LocalDate.of(1990, 1, 1), "3000", true, "MELBOURNE");  // eligible
        seed("teen1", "Sam Young", LocalDate.of(2010, 6, 1), "3000", true, "MELBOURNE");      // under 18
        seed("wills1", "Alex Brown", LocalDate.of(1985, 3, 15), "3058", true, "WILLS");       // other division
        seed("visitor1", "Chris Visitor", LocalDate.of(1980, 1, 1), "3000", false, "MELBOURNE"); // not a citizen
        log.info("Seeded 4 demo voters. Enrolment check that succeeds: Jane Citizen, 1990-01-01, 3000");
    }

    private void seed(String username, String fullName, LocalDate dob, String postcode,
                      boolean citizen, String division) {
        voters.save(Voter.enrol(username, lookupKey.compute(fullName, dob, postcode), dob, citizen, division));
    }
}
