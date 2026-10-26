package com.evp.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SEC-11 cryptography on its own: RFC 9474 RSABSSA-SHA384-PSS-Deterministic. */
class BlindSignerTest {

    private static KeyPair keys;
    private static BlindSigner signer;
    private static TestBlindClient client;

    @BeforeAll
    static void setUp() {
        keys = BlindSigner.generateKeyPair(2048);
        signer = new BlindSigner(keys);
        client = new TestBlindClient((RSAPublicKey) keys.getPublic());
    }

    private static byte[] serial() {
        byte[] s = new byte[32];
        new SecureRandom().nextBytes(s);
        return s;
    }

    @Test
    @DisplayName("SEC-11: a token signature verifies against the election key")
    void roundTripVerifies() {
        byte[] serial = serial();
        TestBlindClient.Blinded blinded = client.blind(serial);
        byte[] signature = client.finalizeSignature(signer.blindSign(blinded.blindedMessage()), blinded.inverse());
        assertThat(signer.verify(serial, signature)).isTrue();
    }

    @Test
    @DisplayName("SEC-11: the signer never sees the unblinded serial")
    void signerInputRevealsNothing() {
        byte[] serial = serial();
        byte[] first = client.blind(serial).blindedMessage();
        byte[] second = client.blind(serial).blindedMessage();
        // what the server receives is not the serial, and blinding the same serial twice
        // gives unrelated values, so the server cannot recognise a serial later
        assertThat(first).isNotEqualTo(serial);
        assertThat(Arrays.equals(first, second)).isFalse();
    }

    @Test
    void signatureForOneSerialDoesNotVerifyAnother() {
        byte[] serial = serial();
        TestBlindClient.Blinded blinded = client.blind(serial);
        byte[] signature = client.finalizeSignature(signer.blindSign(blinded.blindedMessage()), blinded.inverse());
        assertThat(signer.verify(serial(), signature)).isFalse();
    }

    @Test
    void tamperedSignatureRejected() {
        byte[] serial = serial();
        TestBlindClient.Blinded blinded = client.blind(serial);
        byte[] signature = client.finalizeSignature(signer.blindSign(blinded.blindedMessage()), blinded.inverse());
        signature[10] ^= 1;
        assertThat(signer.verify(serial, signature)).isFalse();
    }

    @Test
    void signatureFromAnotherKeyRejected() {
        BlindSigner other = new BlindSigner(BlindSigner.generateKeyPair(2048));
        byte[] serial = serial();
        TestBlindClient.Blinded blinded = client.blind(serial);
        byte[] signature = client.finalizeSignature(signer.blindSign(blinded.blindedMessage()), blinded.inverse());
        assertThat(other.verify(serial, signature)).isFalse();
    }

    @Test
    void malformedBlindedMessagesRejected() {
        assertThatThrownBy(() -> signer.blindSign(new byte[10])).isInstanceOf(IllegalArgumentException.class);
        byte[] tooLarge = new byte[signer.modulusBytes()];
        Arrays.fill(tooLarge, (byte) 0xFF);
        assertThatThrownBy(() -> signer.blindSign(tooLarge)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysUnder2048BitsRejected() {
        assertThatThrownBy(() -> new BlindSigner(BlindSigner.generateKeyPair(1024)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
