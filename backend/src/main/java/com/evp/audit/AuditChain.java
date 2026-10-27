package com.evp.audit;
import com.evp.security.Ed25519Keys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AuditChain {

    public static final String GENESIS = "0".repeat(64);

    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private AuditChain() {
    }

    public record Line(long seq, String recordedAt, String eventType, String actor, String details,
                       String prevHash, String hash, String signature) {

        static Line of(AuditEntry e) {
            return new Line(e.getSeq(), e.getRecordedAt(), e.getEventType(), e.getActor(), e.getDetails(),
                    e.getPrevHash(), e.getHash(), e.getSignature());
        }
    }

    /** Result of a verification. When ok is false, brokenAt is the first bad entry. */
    public record Verification(boolean ok, int entries, long headSeq, String headHash, Long brokenAt, String problem) {
    }

    public static String canonicalDetails(Map<String, String> details) {
        return canonical(new TreeMap<>(details == null ? Map.of() : details));
    }

    public static String hash(String prevHash, long seq, String recordedAt, String eventType, String actor,
                              String details) {
        Map<String, Object> body = new TreeMap<>();
        body.put("seq", seq);
        body.put("recordedAt", recordedAt);
        body.put("eventType", eventType);
        body.put("actor", actor);
        body.put("details", details);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(HexFormat.of().parseHex(prevHash));
            sha.update(canonical(body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static Verification verify(List<Line> lines, PublicKey key, Long anchorSeq, String anchorHash) {
        String prev = GENESIS;
        long expectedSeq = 1;
        for (Line line : lines) {
            if (line.seq() != expectedSeq) {
                return broken(lines, expectedSeq, "sequence gap (found " + line.seq() + "): rows removed or inserted");
            }
            if (!prev.equals(line.prevHash())) {
                return broken(lines, line.seq(), "chain link broken: previous entry was changed");
            }
            String recomputed = hash(prev, line.seq(), line.recordedAt(), line.eventType(), line.actor(), line.details());
            if (!recomputed.equals(line.hash())) {
                return broken(lines, line.seq(), "content was modified after it was written");
            }
            byte[] signature;
            try {
                signature = Base64.getDecoder().decode(line.signature());
            } catch (IllegalArgumentException e) {
                signature = new byte[0];
            }
            if (!Ed25519Keys.verify(key, HexFormat.of().parseHex(line.hash()), signature)) {
                return broken(lines, line.seq(), "signature invalid: entry was not written by the audit service");
            }
            prev = line.hash();
            expectedSeq++;
        }
        if (anchorSeq != null) {
            boolean present = anchorSeq >= 1 && anchorSeq <= lines.size()
                    && lines.get((int) (anchorSeq - 1)).hash().equals(anchorHash);
            if (!present) {
                return broken(lines, anchorSeq, "the published entry " + anchorSeq + " is missing: log truncated or rewritten");
            }
        }
        return new Verification(true, lines.size(), lines.size(), prev, null, null);
    }

    private static Verification broken(List<Line> lines, long at, String problem) {
        return new Verification(false, lines.size(), lines.isEmpty() ? 0 : lines.get(lines.size() - 1).seq(),
                lines.isEmpty() ? GENESIS : lines.get(lines.size() - 1).hash(), at, problem);
    }

    private static String canonical(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise audit entry", e);
        }
    }
}