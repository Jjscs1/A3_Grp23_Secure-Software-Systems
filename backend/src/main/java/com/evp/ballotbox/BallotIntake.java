package com.evp.ballotbox;

import com.evp.crypto.EnvelopeService;
import com.evp.secrecy.BallotWriter;

/**
 * The one place a cast ballot passes through: check the envelope (SEC-12) -> store it separated from the voter
 * (SEC-13) -> append it to the tamper-evident box (SEC-14). Returns the tracking code for the voter.
 * Nothing is added to the box unless the ballot was stored, and nothing is stored unless the envelope is valid.
 */
public class BallotIntake {
    @FunctionalInterface
    public interface Check {
        void requireValid(String envelopeJson) throws Exception;
    }

    @FunctionalInterface
    public interface Store {
        /** Returns the receipt. */
        String insert(String division, String envelopeJson) throws Exception;
    }

    private final Check check;
    private final Store store;
    private final BallotBox box;

    public BallotIntake(Check check, Store store, BallotBox box) {
        this.check = check;
        this.store = store;
        this.box = box;
    }

    public BallotIntake(EnvelopeService envelopes, BallotWriter writer, BallotBox box) {
        this(envelopes::requireValidForCast, writer::insert, box);
    }

    /** @return tracking code "receipt.envelopeHash" (reveals nothing about the vote) */
    public String accept(String division, String envelopeJson) throws Exception {
        check.requireValid(envelopeJson);
        String receipt = store.insert(division, envelopeJson);
        box.accept(receipt, envelopeJson);
        return receipt + "." + BallotLeaf.envelopeHash(envelopeJson);
    }
}
