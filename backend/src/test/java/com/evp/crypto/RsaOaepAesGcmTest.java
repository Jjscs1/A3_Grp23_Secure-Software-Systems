package com.evp.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RsaOaepAesGcmTest {
    static ElectionKey k1;
    static ElectionKey k2;
    final RsaOaepAesGcm alg = new RsaOaepAesGcm();

    @BeforeAll
    static void keys() throws Exception {
        k1 = KeyringLoader.ephemeral("k1", 2048).active();
        k2 = KeyringLoader.ephemeral("k2", 2048).active();
    }

    @Test
    void roundTrip() throws Exception {
        BallotEnvelope e = TestSealer.seal("[3,1,2]", k1);
        assertTrue(alg.validate(e, k1));
        assertArrayEquals("[3,1,2]".getBytes(StandardCharsets.UTF_8), alg.decrypt(e, k1));
    }

    @Test
    void freshContentKeyAndIvEveryTime() throws Exception {
        BallotEnvelope a = TestSealer.seal("[1,2,3]", k1), b = TestSealer.seal("[1,2,3]", k1);
        assertNotEquals(a.ct(), b.ct());
        assertNotEquals(a.ek(), b.ek());
        assertNotEquals(a.iv(), b.iv());
    }

    @Test
    void tamperedCiphertextIsDetected() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        byte[] ct = Base64.getDecoder().decode(e.ct());
        ct[0] ^= 1;
        BallotEnvelope bad = TestSealer.with(e, "ct", Base64.getEncoder().encodeToString(ct));
        assertThrows(GeneralSecurityException.class, () -> alg.decrypt(bad, k1));
    }

    @Test
    void headerIsBoundAsAad_relabellingBreaksDecryption() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        assertThrows(GeneralSecurityException.class, () -> alg.decrypt(TestSealer.with(e, "alg", "OTHER-ALG"), k1));
        assertThrows(GeneralSecurityException.class, () -> alg.decrypt(TestSealer.with(e, "kid", "k9"), k1));
    }

    @Test
    void wrongKeyCannotOpen() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1,2,3]", k1);
        assertThrows(GeneralSecurityException.class, () -> alg.decrypt(e, k2));
    }

    @Test
    void structuralValidationRejectsWrongSizes() throws Exception {
        BallotEnvelope e = TestSealer.seal("[1]", k1);
        assertFalse(alg.validate(TestSealer.with(e, "iv", "QUJD"), k1));
        assertFalse(alg.validate(TestSealer.with(e, "ek", "QUJD"), k1));
        assertFalse(alg.validate(TestSealer.with(e, "ct", "QUJD"), k1));
        assertFalse(alg.validate(TestSealer.with(e, "iv", "***"), k1));
    }
}
