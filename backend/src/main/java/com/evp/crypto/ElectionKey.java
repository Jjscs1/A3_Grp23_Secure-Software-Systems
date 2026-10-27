package com.evp.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HexFormat;

public record ElectionKey(String kid, String alg, PrivateKey privateKey, RSAPublicKey publicKey) {

    public String spkiBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** SHA-256 of the SPKI encoding: publish this so voters can compare it with what their browser shows. */
    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public KeyInfo info() {
        return new KeyInfo(kid, alg, spkiBase64(), fingerprint());
    }

    /** Never print key material. */
    @Override
    public String toString() {
        return "ElectionKey[" + kid + "]";
    }
}
