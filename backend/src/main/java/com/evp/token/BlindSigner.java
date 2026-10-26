package com.evp.token;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Objects;
import javax.crypto.Cipher;

/**
 * SEC-11: server side of RSA blind signatures, RFC 9474 suite
 * RSABSSA-SHA384-PSS-Deterministic.
 *
 * How the voting token works:
 *  1. The voter's browser picks a random serial and blinds it with the election public
 *     key (@cloudflare/blindrsa-ts). The server only ever receives the blinded value.
 *  2. After the eligibility check (SEC-10), the server signs the blinded value here.
 *  3. The browser unblinds the result, producing an ordinary RSA-PSS signature on the
 *     serial that the server has never seen.
 *  4. When the ballot is submitted, the serial and signature are verified here. The
 *     server can confirm the token is genuine, but cannot link it to the voter it
 *     signed for, because it never saw the serial at signing time.
 *
 * Uses only the JDK. The JDK's RSA private key operation applies its own timing
 * blinding to CRT keys, and the result is checked against the public key before it is
 * returned, as RFC 9474 section 4.3 requires, to guard against fault attacks.
 */
public final class BlindSigner {

    public static final String SUITE = "RSABSSA-SHA384-PSS-Deterministic";
    static final int SALT_LENGTH = 48; // RFC 9474: salt length equals the SHA-384 output size

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final int modulusBytes;

    public BlindSigner(KeyPair keyPair) {
        Objects.requireNonNull(keyPair, "keyPair");
        if (!(keyPair.getPrivate() instanceof RSAPrivateCrtKey priv)
                || !(keyPair.getPublic() instanceof RSAPublicKey pub)) {
            throw new IllegalArgumentException("An RSA key pair with a CRT private key is required");
        }
        if (pub.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA modulus must be at least 2048 bits");
        }
        if (!priv.getModulus().equals(pub.getModulus())) {
            throw new IllegalArgumentException("Public and private keys do not match");
        }
        this.privateKey = priv;
        this.publicKey = pub;
        this.modulusBytes = (pub.getModulus().bitLength() + 7) / 8;
    }

    public static KeyPair generateKeyPair(int bits) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(new RSAKeyGenParameterSpec(bits, RSAKeyGenParameterSpec.F4));
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    /**
     * RFC 9474 BlindSign. The input is the blinded message from the client and reveals
     * nothing about the serial.
     */
    public byte[] blindSign(byte[] blindedMessage) {
        Objects.requireNonNull(blindedMessage, "blindedMessage");
        if (blindedMessage.length != modulusBytes) {
            throw new IllegalArgumentException("Blinded message must be exactly " + modulusBytes + " bytes");
        }
        BigInteger m = new BigInteger(1, blindedMessage);
        BigInteger n = publicKey.getModulus();
        if (m.signum() == 0 || m.compareTo(n) >= 0) {
            throw new IllegalArgumentException("Blinded message is out of range");
        }
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
            rsa.init(Cipher.ENCRYPT_MODE, privateKey); // raw private key operation, s = m^d mod n
            byte[] signature = leftPad(rsa.doFinal(blindedMessage), modulusBytes);

            BigInteger check = new BigInteger(1, signature).modPow(publicKey.getPublicExponent(), n);
            if (!check.equals(m)) {
                throw new IllegalStateException("Blind signature failed its self check");
            }
            return signature;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Blind signing failed", e);
        }
    }

    /**
     * Verifies a finalised (unblinded) token: a standard RSASSA-PSS signature over the
     * serial, using SHA-384, MGF1 with SHA-384 and a 48 byte salt.
     */
    public boolean verify(byte[] serial, byte[] signature) {
        if (serial == null || signature == null || signature.length != modulusBytes) {
            return false;
        }
        try {
            Signature pss = Signature.getInstance("RSASSA-PSS");
            pss.setParameter(new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, SALT_LENGTH, 1));
            pss.initVerify(publicKey);
            pss.update(serial);
            return pss.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** X.509 SubjectPublicKeyInfo, which the browser imports with WebCrypto. */
    public byte[] publicKeySpki() {
        return publicKey.getEncoded();
    }

    public int modulusBytes() {
        return modulusBytes;
    }

    private static byte[] leftPad(byte[] value, int length) {
        if (value.length == length) {
            return value;
        }
        if (value.length > length) {
            throw new IllegalStateException("RSA output longer than modulus");
        }
        byte[] padded = new byte[length];
        System.arraycopy(value, 0, padded, length - value.length, value.length);
        return padded;
    }
}
