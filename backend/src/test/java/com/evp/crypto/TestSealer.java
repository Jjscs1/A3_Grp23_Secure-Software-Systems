package com.evp.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Test-only Java version of the browser's sealing code (the real one is frontend/src/security/ballotEnvelope.ts). */
final class TestSealer {
    private TestSealer() { }

    static BallotEnvelope seal(String preferencesJson, ElectionKey key) throws Exception {
        return seal(preferencesJson, key.kid(), key.alg(), key);
    }

    static BallotEnvelope seal(String preferencesJson, String kid, String alg, ElectionKey key) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey cek = kg.generateKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        BallotEnvelope header = new BallotEnvelope(1, alg, kid, "AAAA", "AAAA", "AAAA");

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, cek, new GCMParameterSpec(128, iv));
        aes.updateAAD(RsaOaepAesGcm.aad(header));
        byte[] ct = aes.doFinal(preferencesJson.getBytes(StandardCharsets.UTF_8));

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, key.publicKey(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] ek = rsa.doFinal(cek.getEncoded());

        Base64.Encoder b = Base64.getEncoder();
        return new BallotEnvelope(1, alg, kid, b.encodeToString(ek), b.encodeToString(iv), b.encodeToString(ct));
    }

    static BallotEnvelope with(BallotEnvelope e, String field, String value) {
        return switch (field) {
            case "alg" -> new BallotEnvelope(e.v(), value, e.kid(), e.ek(), e.iv(), e.ct());
            case "kid" -> new BallotEnvelope(e.v(), e.alg(), value, e.ek(), e.iv(), e.ct());
            case "ek" -> new BallotEnvelope(e.v(), e.alg(), e.kid(), value, e.iv(), e.ct());
            case "iv" -> new BallotEnvelope(e.v(), e.alg(), e.kid(), e.ek(), value, e.ct());
            case "ct" -> new BallotEnvelope(e.v(), e.alg(), e.kid(), e.ek(), e.iv(), value);
            default -> throw new IllegalArgumentException(field);
        };
    }

    static String toJson(BallotEnvelope e) {
        return "{\"v\":1,\"alg\":\"" + e.alg() + "\",\"kid\":\"" + e.kid() + "\",\"ek\":\"" + e.ek()
                + "\",\"iv\":\"" + e.iv() + "\",\"ct\":\"" + e.ct() + "\"}";
    }
}
