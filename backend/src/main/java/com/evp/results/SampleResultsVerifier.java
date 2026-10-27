package com.evp.results;

import com.evp.security.Ed25519Keys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * SEC-18: the sample verifier a media outlet runs before broadcasting results.
 * (frontend/src/security/resultsVerifier.ts applies the same rules in the browser.)
 *
 * A publication is ACCEPTED only if all of these hold:
 *   1. Key          its keyId matches the commission's published public key
 *   2. Signature    it verifies with that public key
 *   3. Time         issuedAt is not in the future (5 minutes clock skew allowed)
 *   4. Freshness    its sequence is not LOWER than the newest already accepted for the
 *                   election (stops an attacker replaying an old, superseded result)
 *   5. Consistency  the same sequence never appears with different content, and
 *                   issuedAt never goes backwards
 *
 * The newest accepted publication per election can be kept in a small JSON state file,
 * so replay protection survives restarts.
 */
public class SampleResultsVerifier {

    static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);

    /** The publication must not be trusted; the message says why. */
    public static class Rejected extends RuntimeException {
        public Rejected(String reason) {
            super(reason);
        }
    }

    private final PublicKey trustedKey;
    private final String trustedKeyId;
    private final Path stateFile;
    private final ObjectNode accepted; // electionId -> {sequence, issuedAt, digest}

    public SampleResultsVerifier(String trustedPublicKeyPem, Path stateFile) {
        this.trustedKey = Ed25519Keys.parsePublicKeyPem(trustedPublicKeyPem);
        this.trustedKeyId = ResultsSigning.keyId(trustedPublicKeyPem);
        this.stateFile = stateFile;
        this.accepted = loadState(stateFile);
    }

    /** Returns the payload if it is genuine and fresh; otherwise throws Rejected. */
    public JsonNode verify(JsonNode publication, Instant now) {
        JsonNode payload = publication.path("payload");
        JsonNode signature = publication.path("signature");
        if (!payload.isObject() || !signature.isTextual()) {
            throw new Rejected("not a results publication (needs 'payload' and 'signature')");
        }

        // 1 + 2: genuine?
        if (!trustedKeyId.equals(payload.path("keyId").asText())) {
            throw new Rejected("signed with a different key than the commission's published key");
        }
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(signature.asText());
        } catch (IllegalArgumentException e) {
            sig = new byte[0];
        }
        if (!Ed25519Keys.verify(trustedKey, ResultsSigning.canonicalBytes(payload), sig)) {
            throw new Rejected("SIGNATURE INVALID - the payload was forged or modified");
        }

        // 3 + 4 + 5: fresh?
        String election = payload.path("electionId").asText();
        long sequence = payload.path("sequence").asLong();
        Instant issuedAt = Instant.parse(payload.path("issuedAt").asText());
        String digest = ResultsSigning.digest(payload);
        if (issuedAt.isAfter(now.plus(ALLOWED_CLOCK_SKEW))) {
            throw new Rejected("issuedAt is in the future");
        }
        JsonNode newest = accepted.get(election);
        if (newest != null) {
            long newestSequence = newest.path("sequence").asLong();
            if (sequence < newestSequence) {
                throw new Rejected("REPLAY: sequence " + sequence + " is lower than the already-accepted sequence "
                        + newestSequence);
            }
            if (sequence == newestSequence && !digest.equals(newest.path("digest").asText())) {
                throw new Rejected("CONFLICT: a different payload with sequence " + sequence + " exists");
            }
            if (issuedAt.isBefore(Instant.parse(newest.path("issuedAt").asText()))) {
                throw new Rejected("issuedAt is earlier than the last accepted publication");
            }
        }

        // Accepted: remember it.
        accepted.putObject(election).put("sequence", sequence)
                .put("issuedAt", issuedAt.toString()).put("digest", digest);
        saveState();
        return payload;
    }

    private static ObjectNode loadState(Path file) {
        try {
            if (file != null && Files.exists(file)) {
                return (ObjectNode) ResultsSigning.JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | ClassCastException e) {
            throw new IllegalStateException("Verifier state file is unreadable", e);
        }
        return ResultsSigning.JSON.createObjectNode();
    }

    private void saveState() {
        if (stateFile == null) {
            return;
        }
        try {
            Files.writeString(stateFile, accepted.toPrettyString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save verifier state", e);
        }
    }
}