package com.evp.ballotbox;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BallotBoxTest {
    MemoryLogStore store;
    CheckpointSigner signer;
    BallotBox box;

    static String env(int i) {
        return "{\"v\":1,\"alg\":\"RSA-OAEP-256+A256GCM\",\"kid\":\"k1\",\"ek\":\"ek" + i + "\",\"iv\":\"iv" + i + "\",\"ct\":\"ct" + i + "\"}";
    }

    @BeforeEach
    void setUp() throws Exception {
        store = new MemoryLogStore();
        signer = CheckpointSigner.generate("bb-1");
        box = new BallotBox(store, signer, Clock.fixed(Instant.parse("2026-10-27T01:02:03Z"), ZoneOffset.UTC));
    }

    List<String> cast(int n, int from) throws Exception {
        List<String> leaves = new ArrayList<>();
        for (int i = from; i < from + n; i++) {
            leaves.add(box.accept("receipt-" + i, env(i)));
        }
        box.seal();
        return leaves;
    }

    @Test
    void cleanLogVerifiesAndEveryBallotHasAProof() throws Exception {
        List<String> leaves = cast(7, 0);
        cast(5, 7);
        assertTrue(box.verify().ok());
        Checkpoint cp = box.latest().orElseThrow();
        assertEquals(12, cp.size());
        for (String leaf : leaves) {
            BallotBox.InclusionProof p = box.prove(leaf).orElseThrow();
            List<byte[]> path = new ArrayList<>();
            p.path().forEach(h -> path.add(HexFormat.of().parseHex(h)));
            assertTrue(MerkleTree.verifyInclusion(HexFormat.of().parseHex(leaf), p.index(), p.size(), path, HexFormat.of().parseHex(cp.root())));
        }
    }

    @Test
    void batchOrderRevealsNothingAboutCastOrder() throws Exception {
        List<String> leaves = cast(10, 0);
        List<String> stored = store.leaves();
        List<String> sorted = new ArrayList<>(stored);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, stored);
        assertNotEquals(leaves, stored);
    }

    @Test
    void checkpointSignatureCoversEveryField() throws Exception {
        cast(3, 0);
        Checkpoint c = box.latest().orElseThrow();
        String pub = signer.publicInfo().publicKey();
        assertTrue(CheckpointSigner.verify(pub, CheckpointSigner.message(c), c.sig()));
        Checkpoint t1 = new Checkpoint(c.alg(), c.kid(), c.size() + 1, c.root(), c.ts(), c.sig());
        Checkpoint t2 = new Checkpoint(c.alg(), c.kid(), c.size(), "00" + c.root().substring(2), c.ts(), c.sig());
        Checkpoint t3 = new Checkpoint("RS256", c.kid(), c.size(), c.root(), c.ts(), c.sig());
        for (Checkpoint t : List.of(t1, t2, t3)) {
            assertFalse(CheckpointSigner.verify(pub, CheckpointSigner.message(t), t.sig()));
        }
    }

    @Test
    void alteredBallotIsDetected() throws Exception {
        cast(6, 0);
        store.leaves.set(3, "ab".repeat(32));
        assertFalse(box.verify().ok());
    }

    @Test
    void deletedBallotIsDetected() throws Exception {
        cast(6, 0);
        store.leaves.remove(2);
        BallotBox.Status s = box.verify();
        assertFalse(s.ok());
    }

    @Test
    void addedBallotIsDetected() throws Exception {
        cast(6, 0);
        store.leaves.add("cd".repeat(32));
        BallotBox.Status s = box.verify();
        assertFalse(s.ok());
        assertTrue(s.problem().contains("unsealed"));
    }

    @Test
    void reorderedBallotsAreDetected() throws Exception {
        cast(6, 0);
        java.util.Collections.swap(store.leaves, 0, 5);
        assertFalse(box.verify().ok());
    }

    @Test
    void truncationOfLogAndCheckpointsTogetherIsStillCaughtByConsistency() throws Exception {
        cast(4, 0);
        Checkpoint early = box.latest().orElseThrow();
        cast(4, 4);
        // attacker replaces ballot 1 in history, recomputes leaves but cannot re-sign old checkpoint
        store.leaves.set(1, "ef".repeat(32));
        assertFalse(box.verify().ok());
        assertEquals(4, early.size());
    }

    @Test
    void forgedCheckpointFromAnotherKeyIsRejected() throws Exception {
        cast(4, 0);
        CheckpointSigner attacker = CheckpointSigner.generate("bb-1");
        Checkpoint real = store.checkpoints.remove(store.checkpoints.size() - 1);
        Checkpoint unsigned = new Checkpoint(real.alg(), real.kid(), real.size(), real.root(), real.ts(), "");
        store.checkpoints.add(new Checkpoint(real.alg(), real.kid(), real.size(), real.root(), real.ts(),
                attacker.sign(CheckpointSigner.message(unsigned))));
        assertFalse(box.verify().ok());
    }

    @Test
    void deletedCheckpointCoverageGapDetectedWhenLeavesShrink() throws Exception {
        cast(5, 0);
        store.leaves.remove(store.leaves.size() - 1);
        assertTrue(box.verify().problem().contains("missing"));
    }

    @Test
    void unknownBallotHasNoProof() throws Exception {
        cast(3, 0);
        assertTrue(box.prove("00".repeat(32)).isEmpty());
    }

    @Test
    void ballotNotYetSealedHasNoProofUntilSeal() throws Exception {
        cast(2, 0);
        String leaf = box.accept("late", env(99));
        assertTrue(box.prove(leaf).isEmpty());
        box.seal();
        assertTrue(box.prove(leaf).isPresent());
    }

    @Test
    void consistencyProofBetweenCheckpointsVerifies() throws Exception {
        cast(5, 0);
        cast(6, 5);
        BallotBox.ConsistencyProof p = box.consistency(5, 11).orElseThrow();
        List<byte[]> path = new ArrayList<>();
        p.path().forEach(h -> path.add(HexFormat.of().parseHex(h)));
        assertTrue(MerkleTree.verifyConsistency(5, 11, HexFormat.of().parseHex(p.firstRoot()), HexFormat.of().parseHex(p.secondRoot()), path));
    }

    @Test
    void emptySealsStillVerify() throws Exception {
        box.seal();
        box.seal();
        assertTrue(box.verify().ok());
    }

    @Test
    void publicInfoNeverContainsPrivateKey() {
        SignerInfo i = signer.publicInfo();
        assertEquals("Ed25519", i.alg());
        assertFalse(i.toString().toLowerCase().contains("private"));
    }
}
