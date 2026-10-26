package com.evp.token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Loads the election signing key from a PKCS#8 PEM file ("BEGIN PRIVATE KEY"), such as
 * the one produced by:
 *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out election-signing.pem
 *
 * Keep the file out of the repository (SEC-19). In production this key belongs in an
 * HSM and would be split between trustees (SEC-15).
 */
public final class PemKeys {

    private static final String BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String END = "-----END PRIVATE KEY-----";

    private PemKeys() {
    }

    public static KeyPair loadRsaKeyPair(Path pemFile) {
        try {
            return parseRsaKeyPair(Files.readString(pemFile, StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read election signing key", e);
        }
    }

    public static KeyPair parseRsaKeyPair(String pem) {
        int begin = pem.indexOf(BEGIN);
        int end = pem.indexOf(END);
        if (begin < 0 || end < begin) {
            throw new IllegalArgumentException("Expected a PKCS#8 PEM private key (BEGIN PRIVATE KEY)");
        }
        String base64 = pem.substring(begin + BEGIN.length(), end).replaceAll("\\s", "");
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            PrivateKey priv = rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            if (!(priv instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalArgumentException("Signing key must be an RSA CRT private key");
            }
            PublicKey pub = rsa.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return new KeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key", e);
        }
    }
}
