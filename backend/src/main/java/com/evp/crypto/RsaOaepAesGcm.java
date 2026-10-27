package com.evp.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hybrid scheme matching the browser's WebCrypto code: a fresh AES-256-GCM content key per ballot,
 * wrapped with RSA-OAEP (SHA-256, MGF1-SHA-256). The header (v|alg|kid) is bound as GCM additional
 * authenticated data, so relabelling an envelope to force a downgrade breaks decryption.
 */
public final class RsaOaepAesGcm implements EnvelopeAlgorithm {
    public static final String ID = "RSA-OAEP-256+A256GCM";

    static byte[] aad(BallotEnvelope e) {
        return (e.v() + "|" + e.alg() + "|" + e.kid()).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] b64(String s) {
        return Base64.getDecoder().decode(s);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean validate(BallotEnvelope e, ElectionKey key) {
        try {
            int modulusBytes = (key.publicKey().getModulus().bitLength() + 7) / 8;
            return b64(e.ek()).length == modulusBytes && b64(e.iv()).length == 12 && b64(e.ct()).length >= 17;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public byte[] decrypt(BallotEnvelope e, ElectionKey key) throws GeneralSecurityException {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, key.privateKey(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] cek = rsa.doFinal(b64(e.ek()));
        if (cek.length != 32) {
            throw new GeneralSecurityException("bad content key");
        }
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, b64(e.iv())));
        aes.updateAAD(aad(e));
        return aes.doFinal(b64(e.ct()));
    }
}
