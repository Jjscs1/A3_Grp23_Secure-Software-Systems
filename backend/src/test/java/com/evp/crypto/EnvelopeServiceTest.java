package com.evp.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class EnvelopeServiceTest {
    ElectionKeyring ring;
    AlgorithmRegistry registry;
    EnvelopeService svc;
    ElectionKey k1;
    ElectionKey k2;

    @BeforeEach
    void setUp() throws Exception {
        k1 = KeyringLoader.ephemeral("k1", 2048).active();
        k2 = KeyringLoader.ephemeral("k2", 2048).active();
        ring = new ElectionKeyring(List.of(k1, k2), "k1");
        registry = AlgorithmRegistry.withDefaults();
        svc = new EnvelopeService(registry, () -> ring);
    }

    private String code(Executable x) {
        return assertThrows(InvalidEnvelopeException.class, x).code();
    }

    @Test
    void validEnvelopeIsAcceptedAndOpens() throws Exception {
        BallotEnvelope e = svc.requireValidForCast(TestSealer.toJson(TestSealer.seal("[3,1,2]", k1)));
        assertEquals(List.of(3L, 1L, 2L), svc.open(e));
    }

    @Test
    void plaintextPreferencesAndUnknownFieldsAreRefused() {
        assertEquals("malformed_envelope", code(() -> svc.parse("{\"preferences\":[1,2,3]}")));
        assertEquals("malformed_envelope", code(() -> svc.parse("not json")));
        assertEquals("malformed_envelope", code(() -> svc.parse(null)));
        assertEquals("malformed_envelope", code(() -> svc.parse("{\"v\":1,\"alg\":\"RSA-OAEP-256+A256GCM\",\"kid\":\"k1\",\"ek\":\"QUJD\",\"iv\":\"QUJD\",\"ct\":\"QUJD\",\"extra\":1}")));
        assertEquals("malformed_envelope", code(() -> svc.parse("x".repeat(9000))));
    }

    @Test
    void formatIsEnforcedBeforeAnyCryptoRuns() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1]", k1);
        assertEquals("malformed_envelope", code(() -> svc.requireValidForCast(TestSealer.with(e, "alg", "<script>"))));
        assertEquals("malformed_envelope", code(() -> svc.requireValidForCast(TestSealer.with(e, "ct", "A".repeat(5000)))));
        assertEquals("malformed_envelope", code(() -> svc.requireValidForCast(TestSealer.with(e, "iv", "QUJD"))));
    }

    @Test
    void unknownAlgorithmOrKeyIsRefused() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1]", k1);
        assertEquals("unknown_or_retired_key", code(() -> svc.requireValidForCast(TestSealer.with(e, "kid", "nope"))));
        assertEquals("unsupported_algorithm", code(() -> svc.requireValidForCast(TestSealer.with(e, "alg", "NONE-0"))));
    }

    @Test
    void tamperedBallotIsAcceptedSealedButCountsAsInformal() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        BallotEnvelope bad = TestSealer.with(e, "ct", "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo=");
        svc.requireValidForCast(bad); // the server cannot tell at cast time
        assertEquals("cannot_open", code(() -> svc.open(bad)));
    }

    @Test
    void relabellingDoesNotDowngrade() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        assertEquals("cannot_open", code(() -> svc.open(TestSealer.with(e, "alg", "SOMETHING-ELSE"))));
    }

    @Test
    void keyRotation_oldKeyRetiredForCastingButStillCounts() throws Exception {
        BallotEnvelope old = TestSealer.seal("[1,2,3]", k1);
        ring.setActive("k2");
        assertEquals("unknown_or_retired_key", code(() -> svc.requireValidForCast(old)));
        assertEquals(List.of(1L, 2L, 3L), svc.open(old));
        BallotEnvelope fresh = TestSealer.seal("[3,2,1]", k2);
        svc.requireValidForCast(fresh);
        assertEquals(List.of(3L, 2L, 1L), svc.open(fresh));
        assertEquals("k2", svc.publicKeyInfo().kid());
    }

    @Test
    void deprecatedAlgorithm_noNewBallotsButExistingStillCount() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        registry.setStatus(RsaOaepAesGcm.ID, AlgorithmRegistry.Status.DEPRECATED);
        assertEquals("unsupported_algorithm", code(() -> svc.requireValidForCast(e)));
        assertEquals(List.of(1L, 2L, 3L), svc.open(e));
    }

    @Test
    void newAlgorithmCanBeAddedWithoutTouchingTheService() throws Exception {
        final String newId = "RSA-OAEP-256+A256GCM-V2";
        RsaOaepAesGcm inner = new RsaOaepAesGcm();
        registry.register(new EnvelopeAlgorithm() {
            public String id() { return newId; }
            public boolean validate(BallotEnvelope e, ElectionKey k) { return inner.validate(e, k); }
            public byte[] decrypt(BallotEnvelope e, ElectionKey k) throws GeneralSecurityException { return inner.decrypt(e, k); }
        }, AlgorithmRegistry.Status.ACTIVE);
        ElectionKey v2 = new ElectionKey("k3", newId, k1.privateKey(), k1.publicKey());
        ring = new ElectionKeyring(List.of(k1, v2), "k3");
        BallotEnvelope e = TestSealer.seal("[2,1]", "k3", newId, v2);
        svc.requireValidForCast(e);
        assertEquals(List.of(2L, 1L), svc.open(e));
        assertEquals(newId, svc.publicKeyInfo().alg());
    }

    @Test
    void nonArrayContentCountsAsInformal() throws Exception {
        BallotEnvelope e = TestSealer.seal("{\"hello\":1}", k1);
        assertEquals("cannot_open", code(() -> svc.open(e)));
    }

    @Test
    void formalityMeansEveryCandidateRankedOnce() {
        Set<Long> c = Set.of(1L, 2L, 3L);
        assertTrue(EnvelopeService.isFormal(List.of(3L, 1L, 2L), c));
        assertFalse(EnvelopeService.isFormal(List.of(1L, 2L), c));
        assertFalse(EnvelopeService.isFormal(List.of(1L, 1L, 2L), c));
        assertFalse(EnvelopeService.isFormal(List.of(1L, 2L, 9L), c));
        assertFalse(EnvelopeService.isFormal(null, c));
    }

    @Test
    void errorsNeverEchoInput() {
        String c = code(() -> svc.parse("{\"alg\":\"<script>alert(1)</script>\"}"));
        assertFalse(c.contains("script"));
    }
}
