package com.evp.token;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * Test-only Java version of what the browser does with @cloudflare/blindrsa-ts:
 * RFC 9474 RSABSSA-SHA384-PSS-Deterministic Blind and Finalize. Lets the Spring tests
 * run the whole token flow without a browser.
 */
public final class TestBlindClient {

    public record Blinded(byte[] blindedMessage, BigInteger inverse) {
    }

    private static final int HASH_LEN = 48;
    private static final int SALT_LEN = 48;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RSAPublicKey publicKey;
    private final int k;

    public TestBlindClient(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
        this.k = (publicKey.getModulus().bitLength() + 7) / 8;
    }

    public Blinded blind(byte[] message) {
        BigInteger n = publicKey.getModulus();
        byte[] encoded = emsaPssEncode(message, n.bitLength() - 1);
        BigInteger m = new BigInteger(1, encoded);
        BigInteger r;
        do {
            r = new BigInteger(n.bitLength(), RANDOM);
        } while (r.signum() == 0 || r.compareTo(n) >= 0 || !r.gcd(n).equals(BigInteger.ONE));
        BigInteger z = m.multiply(r.modPow(publicKey.getPublicExponent(), n)).mod(n);
        return new Blinded(toBytes(z, k), r.modInverse(n));
    }

    public byte[] finalizeSignature(byte[] blindSignature, BigInteger inverse) {
        BigInteger n = publicKey.getModulus();
        BigInteger s = new BigInteger(1, blindSignature).multiply(inverse).mod(n);
        return toBytes(s, k);
    }

    private static byte[] emsaPssEncode(byte[] message, int emBits) {
        int emLen = (emBits + 7) / 8;
        byte[] mHash = sha384(message);
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);

        byte[] mPrime = new byte[8 + HASH_LEN + SALT_LEN];
        System.arraycopy(mHash, 0, mPrime, 8, HASH_LEN);
        System.arraycopy(salt, 0, mPrime, 8 + HASH_LEN, SALT_LEN);
        byte[] h = sha384(mPrime);

        byte[] db = new byte[emLen - HASH_LEN - 1];
        db[db.length - SALT_LEN - 1] = 0x01;
        System.arraycopy(salt, 0, db, db.length - SALT_LEN, SALT_LEN);
        byte[] mask = mgf1(h, db.length);
        for (int i = 0; i < db.length; i++) {
            db[i] ^= mask[i];
        }
        db[0] &= (byte) (0xFF >>> (8 * emLen - emBits));

        byte[] em = new byte[emLen];
        System.arraycopy(db, 0, em, 0, db.length);
        System.arraycopy(h, 0, em, db.length, HASH_LEN);
        em[emLen - 1] = (byte) 0xBC;
        return em;
    }

    private static byte[] mgf1(byte[] seed, int length) {
        byte[] out = new byte[length];
        int done = 0;
        for (int counter = 0; done < length; counter++) {
            byte[] input = Arrays.copyOf(seed, seed.length + 4);
            input[seed.length] = (byte) (counter >>> 24);
            input[seed.length + 1] = (byte) (counter >>> 16);
            input[seed.length + 2] = (byte) (counter >>> 8);
            input[seed.length + 3] = (byte) counter;
            byte[] block = sha384(input);
            int take = Math.min(block.length, length - done);
            System.arraycopy(block, 0, out, done, take);
            done += take;
        }
        return out;
    }

    private static byte[] sha384(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] toBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] out = new byte[length];
        int copy = Math.min(raw.length, length);
        System.arraycopy(raw, raw.length - copy, out, length - copy, copy);
        return out;
    }
}
