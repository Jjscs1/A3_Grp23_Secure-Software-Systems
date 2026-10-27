package com.evp.ballotbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * How a stored ballot becomes a Merkle leaf. The browser recomputes exactly the same value
 * (frontend/src/security/ballotBox.ts), so a voter can find their own ballot without trusting the server.
 * leaf = H(0x00 || "evote-ballot-v1|" + receipt + "|" + SHA-256(JSON [v,alg,kid,ek,iv,ct])).
 */
public final class BallotLeaf {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BallotLeaf() { }

    /** Hex SHA-256 of the canonical envelope array, identical to the browser's JSON.stringify([...]). */
    public static String envelopeHash(String envelopeJson) {
        try {
            JsonNode n = JSON.readTree(envelopeJson);
            String canonical = JSON.writeValueAsString(new Object[] {
                    n.get("v").asInt(), text(n, "alg"), text(n, "kid"), text(n, "ek"), text(n, "iv"), text(n, "ct")});
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("not a ballot envelope");
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || !v.isTextual()) {
            throw new IllegalArgumentException("missing " + f);
        }
        return v.asText();
    }

    public static String leafData(String receipt, String envelopeHashHex) {
        return "evote-ballot-v1|" + receipt + "|" + envelopeHashHex;
    }

    public static byte[] leaf(String receipt, String envelopeJson) {
        return MerkleTree.leafHash(leafData(receipt, envelopeHash(envelopeJson)).getBytes(StandardCharsets.UTF_8));
    }

    public static String leafHex(String receipt, String envelopeJson) {
        return HexFormat.of().formatHex(leaf(receipt, envelopeJson));
    }
}
