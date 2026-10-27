package com.evp.ballotbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BallotLeafTest {
    static final String ENV = "{\"v\":1,\"alg\":\"A\",\"kid\":\"k\",\"ek\":\"e\",\"iv\":\"i\",\"ct\":\"c\"}";

    @Test
    void envelopeHashIsStableAcrossKeyOrderAndWhitespace() {
        String reordered = "{ \"ct\":\"c\", \"iv\":\"i\", \"ek\":\"e\", \"kid\":\"k\", \"alg\":\"A\", \"v\":1 }";
        assertEquals(BallotLeaf.envelopeHash(ENV), BallotLeaf.envelopeHash(reordered));
    }

    @Test
    void leafChangesWithReceiptOrEnvelope() {
        String a = BallotLeaf.leafHex("r1", ENV);
        assertNotEquals(a, BallotLeaf.leafHex("r2", ENV));
        assertNotEquals(a, BallotLeaf.leafHex("r1", ENV.replace("\"c\"", "\"d\"")));
        assertEquals(64, a.length());
    }

    @Test
    void leafDoesNotContainVote() {
        assertFalse(BallotLeaf.leafData("r1", BallotLeaf.envelopeHash(ENV)).contains("\"ct\""));
    }

    @Test
    void rejectsNonEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> BallotLeaf.envelopeHash("{\"x\":1}"));
        assertThrows(IllegalArgumentException.class, () -> BallotLeaf.envelopeHash("nope"));
    }

    @Test
    void printVectorForBrowserTest() {
        System.out.println("VECTOR " + BallotLeaf.envelopeHash(ENV) + " " + BallotLeaf.leafHex("r1", ENV));
    }
}
