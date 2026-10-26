package com.evp.ballot;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEC-11: spends the token and stores the ballot in one transaction, so either both
 * happen or neither does. Separate bean from BallotSubmissionService so that a failed
 * attempt can be retried in a fresh transaction.
 */
@Component
public class BallotWriter {

    private final SpentTokenRepository spentTokens;
    private final BallotSink ballotSink;

    public BallotWriter(SpentTokenRepository spentTokens, BallotSink ballotSink) {
        this.spentTokens = spentTokens;
        this.ballotSink = ballotSink;
    }

    @Transactional
    public void spendAndStore(String serialHash, String encryptedBallotEnvelope) {
        spentTokens.saveAndFlush(new SpentToken(serialHash)); // UNIQUE: throws if already spent
        ballotSink.store(encryptedBallotEnvelope);
    }
}
