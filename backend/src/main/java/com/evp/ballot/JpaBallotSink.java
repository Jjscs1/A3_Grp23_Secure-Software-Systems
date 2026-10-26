package com.evp.ballot;

import java.time.Clock;
import org.springframework.stereotype.Component;

/** Placeholder BallotSink. Replace with the SEC-12/13/14 implementation when it exists. */
@Component
public class JpaBallotSink implements BallotSink {

    private final StoredBallotRepository ballots;
    private final Clock clock;

    public JpaBallotSink(StoredBallotRepository ballots, Clock clock) {
        this.ballots = ballots;
        this.clock = clock;
    }

    @Override
    public void store(String encryptedBallotEnvelope) {
        ballots.save(new StoredBallot(encryptedBallotEnvelope, clock.instant()));
    }
}
