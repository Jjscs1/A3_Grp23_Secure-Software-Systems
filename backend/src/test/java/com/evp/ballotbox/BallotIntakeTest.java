package com.evp.ballotbox;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BallotIntakeTest {
    MemoryLogStore store = new MemoryLogStore();
    BallotBox box;
    List<String> stored = new ArrayList<>();

    BallotIntakeTest() throws Exception {
        box = new BallotBox(store, CheckpointSigner.generate("bb-1"), Clock.systemUTC());
    }

    @Test
    void validBallotIsCheckedStoredAndAppendedAndTrackingCodeVerifies() throws Exception {
        BallotIntake in = new BallotIntake(j -> { }, (d, j) -> { stored.add(j); return "REC1"; }, box);
        String json = BallotBoxTest.env(1);
        String code = in.accept("div", json);
        assertEquals("REC1." + BallotLeaf.envelopeHash(json), code);
        assertEquals(1, stored.size());
        box.seal();
        assertTrue(box.prove(BallotLeaf.leafHex("REC1", json)).isPresent());
        assertTrue(box.verify().ok());
    }

    @Test
    void invalidEnvelopeIsNeitherStoredNorAppended() throws Exception {
        BallotIntake in = new BallotIntake(j -> { throw new IllegalArgumentException("bad"); }, (d, j) -> { stored.add(j); return "R"; }, box);
        assertThrows(IllegalArgumentException.class, () -> in.accept("div", BallotBoxTest.env(1)));
        assertTrue(stored.isEmpty());
        assertTrue(store.pending.isEmpty());
    }

    @Test
    void storageFailureMeansNoLeaf() throws Exception {
        BallotIntake in = new BallotIntake(j -> { }, (d, j) -> { throw new java.sql.SQLException("down"); }, box);
        assertThrows(java.sql.SQLException.class, () -> in.accept("div", BallotBoxTest.env(1)));
        assertTrue(store.pending.isEmpty());
    }
}
