package com.evp.results;

import com.evp.security.Ed25519Keys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * SEC-18: how a results payload is signed and checked. Shared by the publisher, the Java
 * sample verifier and (with the same rules) frontend/src/security/resultsVerifier.ts.
 *
 * Algorithm: Ed25519 over the CANONICAL JSON of the payload: keys sorted, no spaces.
 * The same data must always give exactly the same bytes, otherwise a genuine payload
 * would fail to verify after being re-serialised. Payloads therefore contain only
 * strings, integers, lists and objects (no decimals, which languages print differently).
 */
public final class ResultsSigning {

    public static final String ALGORITHM = "Ed25519";
    static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private ResultsSigning() {
    }

    public static byte[] canonicalBytes(JsonNode payload) {
        try {
            Object plain = JSON.convertValue(payload, Object.class); // nested Maps, so keys can be sorted
            return CANONICAL.writeValueAsString(plain).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise payload", e);
        }
    }

    public static String sign(PrivateKey key, JsonNode payload) {
        return Base64.getEncoder().encodeToString(Ed25519Keys.sign(key, canonicalBytes(payload)));
    }

    /** A short fingerprint of the public key, so verifiers know which key signed a payload. */
    public static String keyId(String publicKeyPem) {
        return sha256Hex(publicKeyPem.strip().getBytes(StandardCharsets.US_ASCII)).substring(0, 16);
    }

    public static String digest(JsonNode payload) {
        return sha256Hex(canonicalBytes(payload));
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** {"payload": ..., "signature": ...}: what is published and what verifiers receive. */
    public static JsonNode publication(JsonNode payload, String signature) {
        return JSON.valueToTree(Map.of("payload", payload, "signature", signature));
    }
}