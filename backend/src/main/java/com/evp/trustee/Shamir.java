package com.evp.trustee;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Shamir {

    static final BigInteger PRIME = BigInteger.TWO.pow(521).subtract(BigInteger.ONE);
    static final int SECRET_BYTES = 32;
    private static final String VERSION = "evs1";
    private static final int VALUE_HEX_DIGITS = 132; 
    private static final SecureRandom RANDOM = new SecureRandom();

    private Shamir() {
    }

    record Share(int index, BigInteger value) {

        String encode() {
            String hex = String.format("%0" + VALUE_HEX_DIGITS + "x", value);
            String body = VERSION + "-" + index + "-" + hex;
            return body + "-" + checksum(body);
        }

        static Share decode(String text) {
            String[] parts = text == null ? new String[0] : text.trim().toLowerCase().split("-");
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw new ShareException("A share is not in the evs1 format.");
            }
            String body = parts[0] + "-" + parts[1] + "-" + parts[2];
            if (!checksum(body).equals(parts[3])) {
                throw new ShareException("A share has a typo (checksum does not match).");
            }
            try {
                int index = Integer.parseInt(parts[1]);
                BigInteger value = new BigInteger(parts[2], 16);
                if (index < 1 || index > 255 || value.compareTo(PRIME) >= 0) {
                    throw new ShareException("A share is out of range.");
                }
                return new Share(index, value);
            } catch (NumberFormatException e) {
                throw new ShareException("A share is not in the evs1 format.");
            }
        }
    }

    public static List<String> split(byte[] secret, int threshold, int count) {
        if (secret.length != SECRET_BYTES) {
            throw new IllegalArgumentException("Secret must be " + SECRET_BYTES + " bytes");
        }
        if (threshold < 2 || threshold > count || count > 255) {
            throw new IllegalArgumentException("Need 2 <= threshold <= count <= 255");
        }
        
        List<BigInteger> coefficients = new ArrayList<>();
        coefficients.add(new BigInteger(1, secret));
        for (int i = 1; i < threshold; i++) {
            coefficients.add(new BigInteger(PRIME.bitLength() + 64, RANDOM).mod(PRIME));
        }

        List<String> shares = new ArrayList<>();
        for (int x = 1; x <= count; x++) {
            BigInteger y = BigInteger.ZERO;
            for (int i = coefficients.size() - 1; i >= 0; i--) { // Horner's rule
                y = y.multiply(BigInteger.valueOf(x)).add(coefficients.get(i)).mod(PRIME);
            }
            shares.add(new Share(x, y).encode());
        }
        return shares;
    }

    @throws ShareException 
    public static byte[] combine(List<String> shareTexts, int threshold) {
        Map<Integer, Share> unique = new LinkedHashMap<>(); 
        for (String text : shareTexts) {
            Share share = Share.decode(text);
            unique.put(share.index(), share);
        }
        if (unique.size() < threshold) {
            throw new NotEnoughSharesException(threshold, unique.size());
        }

        List<Share> points = new ArrayList<>(unique.values()).subList(0, threshold);
        BigInteger secret = BigInteger.ZERO;
        for (Share si : points) {
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;
            for (Share sj : points) {
                if (si.index() != sj.index()) {
                    numerator = numerator.multiply(BigInteger.valueOf(-sj.index())).mod(PRIME);
                    denominator = denominator.multiply(BigInteger.valueOf(si.index() - sj.index())).mod(PRIME);
                }
            }
            BigInteger term = si.value().multiply(numerator).multiply(denominator.modInverse(PRIME));
            secret = secret.add(term).mod(PRIME);
        }
        return toFixedBytes(secret);
    }

    private static byte[] toFixedBytes(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[SECRET_BYTES];
        int copy = Math.min(raw.length, SECRET_BYTES);
        System.arraycopy(raw, raw.length - copy, out, SECRET_BYTES - copy, copy);
        return out;
    }

    private static String checksum(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}