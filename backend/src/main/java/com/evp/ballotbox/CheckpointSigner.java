package com.evp.ballotbox;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Ed25519 signer for tree heads. The algorithm name is part of the signed message (crypto-agility), and the
 * signing key is separate from the election encryption key (separation of duties).
 */
public final class CheckpointSigner {
    public static final String ALG = "Ed25519";

    private final String kid;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private CheckpointSigner(String kid, PrivateKey privateKey, PublicKey publicKey) {
        this.kid = kid;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /** Throw-away key for demos/tests: published roots cannot be verified after a restart. */
    public static CheckpointSigner generate(String kid) throws GeneralSecurityException {
        KeyPair kp = KeyPairGenerator.getInstance(ALG).generateKeyPair();
        return new CheckpointSigner(kid, kp.getPrivate(), kp.getPublic());
    }

    /** Keys from PEM text: PKCS#8 private key and X.509 public key (both generated with openssl; private one git-ignored). */
    public static CheckpointSigner fromPem(String kid, String privatePem, String publicPem) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance(ALG);
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(strip(privatePem))));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(strip(publicPem))));
        return new CheckpointSigner(kid, priv, pub);
    }

    private static String strip(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }

    public String kid() {
        return kid;
    }

    public String alg() {
        return ALG;
    }

    public static String message(Checkpoint c) {
        return "ballotbox-v1|" + c.alg() + "|" + c.kid() + "|" + c.size() + "|" + c.root() + "|" + c.ts();
    }

    public String sign(String message) throws GeneralSecurityException {
        Signature s = Signature.getInstance(ALG);
        s.initSign(privateKey);
        s.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    public SignerInfo publicInfo() {
        byte[] spki = publicKey.getEncoded();
        try {
            return new SignerInfo(kid, ALG, Base64.getEncoder().encodeToString(spki),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(spki)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String spkiBase64, String message, String signatureBase64) {
        try {
            PublicKey pub = KeyFactory.getInstance(ALG).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)));
            Signature s = Signature.getInstance(ALG);
            s.initVerify(pub);
            s.update(message.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
