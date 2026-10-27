package com.evp.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evp.security.Ed25519Keys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SEC-18: signing and the media outlet's sample verifier, without Spring. */
class SampleResultsVerifierTest {

    private final KeyPair commission = Ed25519Keys.generate();
    private final String publishedKeyPem = Ed25519Keys.publicKeyPem(commission.getPublic());
    private final Instant now = Instant.parse("2026-05-02T12:00:00Z");

    /** A signed publication, as ResultsPublisher makes it. */
    private JsonNode publication(KeyPair key, long election, long sequence, Instant issuedAt, String winner) {
        ObjectNode payload = ResultsSigning.JSON.createObjectNode();
        payload.put("schema", ResultsPublisher.SCHEMA);
        payload.put("electionId", election);
        payload.put("sequence", sequence);
        payload.put("issuedAt", issuedAt.toString());
        payload.put("keyId", ResultsSigning.keyId(Ed25519Keys.publicKeyPem(key.getPublic())));
        payload.putArray("results").addObject().put("contest", "house").put("region", "MELBOURNE")
                .putArray("elected").addObject().put("id", 1).put("name", winner);
        return ResultsSigning.publication(payload, ResultsSigning.sign(key.getPrivate(), payload));
    }

    private JsonNode v1() {
        return publication(commission, 1, 1, now.minusSeconds(60), "NGUYEN, Ava");
    }

    private JsonNode v2() {
        return publication(commission, 1, 2, now, "SINGH, Maya");
    }

    private SampleResultsVerifier outlet() {
        return new SampleResultsVerifier(publishedKeyPem, null);
    }

    private static ObjectNode copy(JsonNode node) {
        return node.deepCopy();
    }

    // ===== Criterion 1: the results payload signature verifies with the published public key

    @Test
    @DisplayName("SEC-18: the signature verifies with the published public key")
    void signatureVerifies() {
        JsonNode pub = v1();
        assertThat(Ed25519Keys.verify(Ed25519Keys.parsePublicKeyPem(publishedKeyPem),
                ResultsSigning.canonicalBytes(pub.get("payload")),
                java.util.Base64.getDecoder().decode(pub.get("signature").asText()))).isTrue();
        assertThat(outlet().verify(pub, now).path("sequence").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-18: it still verifies after travelling as JSON text")
    void verifiesAfterJsonRoundTrip() throws Exception {
        JsonNode received = ResultsSigning.JSON.readTree(v1().toString());
        assertThat(outlet().verify(received, now).path("sequence").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-18: a changed winner is rejected")
    void changedResultRejected() {
        ObjectNode forged = copy(v2());
        ((ObjectNode) forged.get("payload").get("results").get(0).get("elected").get(0)).put("name", "SOMEONE ELSE");
        assertThatThrownBy(() -> outlet().verify(forged, now)).hasMessageContaining("SIGNATURE INVALID");
    }

    @Test
    @DisplayName("SEC-18: a payload signed by another key is rejected")
    void otherKeyRejected() {
        JsonNode impostor = publication(Ed25519Keys.generate(), 1, 1, now, "FAKE");
        assertThatThrownBy(() -> outlet().verify(impostor, now)).hasMessageContaining("different key");
    }

    // ===== Criterion 2: a payload with a lower sequence number is rejected by the sample verifier

    @Test
    @DisplayName("SEC-18: a lower sequence number is rejected (replayed old result)")
    void lowerSequenceRejected() {
        SampleResultsVerifier outlet = outlet();
        outlet.verify(v2(), now);
        assertThatThrownBy(() -> outlet.verify(v1(), now))
                .isInstanceOf(SampleResultsVerifier.Rejected.class)
                .hasMessageContaining("REPLAY: sequence 1 is lower than the already-accepted sequence 2");
    }

    @Test
    @DisplayName("SEC-18: newer versions are accepted in order")
    void newerAccepted() {
        SampleResultsVerifier outlet = outlet();
        outlet.verify(v1(), now);
        assertThat(outlet.verify(v2(), now).path("sequence").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("SEC-18: the sequence number cannot be edited (it is signed)")
    void sequenceCannotBeEdited() {
        ObjectNode bumped = copy(v1());
        ((ObjectNode) bumped.get("payload")).put("sequence", 99);
        assertThatThrownBy(() -> outlet().verify(bumped, now)).hasMessageContaining("SIGNATURE INVALID");
    }

    @Test
    @DisplayName("SEC-18: two different payloads with the same sequence number are rejected")
    void conflictRejected() {
        SampleResultsVerifier outlet = outlet();
        outlet.verify(v1(), now);
        JsonNode sameNumberOtherContent = publication(commission, 1, 1, now.minusSeconds(60), "SOMEONE ELSE");
        assertThatThrownBy(() -> outlet.verify(sameNumberOtherContent, now)).hasMessageContaining("CONFLICT");
    }

    @Test
    @DisplayName("SEC-18: replay protection survives a restart of the verifier")
    void survivesRestart(@TempDir Path dir) {
        Path state = dir.resolve("verifier_state.json");
        new SampleResultsVerifier(publishedKeyPem, state).verify(v2(), now);
        SampleResultsVerifier restarted = new SampleResultsVerifier(publishedKeyPem, state);
        assertThatThrownBy(() -> restarted.verify(v1(), now)).hasMessageContaining("REPLAY");
    }

    @Test
    @DisplayName("SEC-18: elections are tracked separately")
    void electionsSeparate() {
        SampleResultsVerifier outlet = outlet();
        outlet.verify(v2(), now);
        assertThat(outlet.verify(publication(commission, 2, 1, now, "X"), now).path("electionId").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("SEC-18: a timestamp in the future is rejected")
    void futureRejected() {
        JsonNode future = publication(commission, 1, 1, now.plus(Duration.ofHours(1)), "X");
        assertThatThrownBy(() -> outlet().verify(future, now)).hasMessageContaining("future");
    }
}