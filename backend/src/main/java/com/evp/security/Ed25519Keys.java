package com.evp.security;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Ed25519Keys {

    private Ed25519Keys() {
    }

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }

    public static KeyPair load(Path privatePem, Path publicPem) {
        try {
            KeyFactory ed = KeyFactory.getInstance("Ed25519");
            PrivateKey priv = ed.generatePrivate(new PKCS8EncodedKeySpec(pemBody(privatePem, "PRIVATE KEY")));
            PublicKey pub = ed.generatePublic(new X509EncodedKeySpec(pemBody(publicPem, "PUBLIC KEY")));
            KeyPair pair = new KeyPair(pub, priv);
            byte[] probe = "key-pair-check".getBytes(StandardCharsets.US_ASCII);
            if (!verify(pub, probe, sign(priv, probe))) {
                throw new IllegalStateException("The public key does not match the private key");
            }
            return pair;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid Ed25519 key file", e);
        }
    }

    public static byte[] sign(PrivateKey key, byte[] message) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(message);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    public static boolean verify(PublicKey key, byte[] message, byte[] signature) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(message);
            return s.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String publicKeyPem(PublicKey key) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static PublicKey parsePublicKeyPem(String pem) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(body(pem, "PUBLIC KEY")));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an Ed25519 public key PEM", e);
        }
    }

    private static byte[] pemBody(Path file, String type) {
        try {
            return body(Files.readString(file, StandardCharsets.US_ASCII), type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read key file " + file.getFileName(), e);
        }
    }

    private static byte[] body(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int b = pem.indexOf(begin);
        int e = pem.indexOf(end);
        if (b < 0 || e < b) {
            throw new IllegalArgumentException("Expected a PEM block: " + begin);
        }
        return Base64.getDecoder().decode(pem.substring(b + begin.length(), e).replaceAll("\\s", ""));
    }
}