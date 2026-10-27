package com.evp.tally;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class MerkleRoot {

    private MerkleRoot() {
    }

    public static String of(List<String> receipts) {
        if (receipts.isEmpty()) {
            return HexFormat.of().formatHex(sha256(new byte[] {2}, "empty".getBytes(StandardCharsets.UTF_8)));
        }
        List<String> sorted = new ArrayList<>(receipts);
        sorted.sort(null);
        List<byte[]> level = new ArrayList<>();
        for (String r : sorted) {
            level.add(sha256(new byte[] {0}, r.getBytes(StandardCharsets.UTF_8)));
        }
        while (level.size() > 1) {
            if (level.size() % 2 == 1) {
                level.add(level.get(level.size() - 1)); 
            }
            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                next.add(sha256(new byte[] {1}, level.get(i), level.get(i + 1)));
            }
            level = next;
        }
        return HexFormat.of().formatHex(level.get(0));
    }

    private static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}