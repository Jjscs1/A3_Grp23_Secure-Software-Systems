package com.evp.audit;
import static org.assertj.core.api.Assertions.assertThat;
import com.evp.security.Ed25519Keys;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditChainTest {

    private final KeyPair key = Ed25519Keys.generate();
    private List<AuditChain.Line> log;

    private AuditChain.Line write(long seq, String prev, String event, String actor, Map<String, String> details) {
        String recordedAt = "2026-05-02T08:00:0" + seq + "Z";
        String canonical = AuditChain.canonicalDetails(details);
        String hash = AuditChain.hash(prev, seq, recordedAt, event, actor, canonical);
        String sig = Base64.getEncoder().encodeToString(Ed25519Keys.sign(key.getPrivate(), HexFormat.of().parseHex(hash)));
        return new AuditChain.Line(seq, recordedAt, event, actor, canonical, prev, hash, sig);
    }

    @BeforeEach
    void fiveEntries() {
        log = new ArrayList<>();
        String prev = AuditChain.GENESIS;
        String[] events = {"LOGIN", "CHANGE_PROPOSED", "CHANGE_APPROVED", "POLL_CLOSED", "TALLY_PUBLISHED"};
        for (int i = 0; i < events.length; i++) {
            AuditChain.Line line = write(i + 1, prev, events[i], "delegate" + i, Map.of("ip", "10.0.0." + i));
            log.add(line);
            prev = line.hash();
        }
    }

    private AuditChain.Verification verify(List<AuditChain.Line> lines) {
        return AuditChain.verify(lines, key.getPublic(), null, null);
    }

    @Test
    @DisplayName("SEC-17: an untouched log verifies")
    void intactLogVerifies() {
        AuditChain.Verification v = verify(log);
        assertThat(v.ok()).isTrue();
        assertThat(v.headSeq()).isEqualTo(5);
    }

    @Test
    @DisplayName("SEC-17: editing one row breaks hash-chain verification at that row")
    void editedRowBreaksChain() {
        AuditChain.Line e = log.get(2);
        log.set(2, new AuditChain.Line(e.seq(), e.recordedAt(), e.eventType(), "someone-else", e.details(),
                e.prevHash(), e.hash(), e.signature()));
        AuditChain.Verification v = verify(log);
        assertThat(v.ok()).isFalse();
        assertThat(v.brokenAt()).isEqualTo(3);
        assertThat(v.problem()).contains("modified");
    }

    @Test
    @DisplayName("SEC-17: recomputing the hash after an edit is caught by the signature")
    void rehashedEditCaughtBySignature() {
        AuditChain.Line e = log.get(4);
        String forgedDetails = AuditChain.canonicalDetails(Map.of("ip", "1.2.3.4"));
        String forgedHash = AuditChain.hash(e.prevHash(), e.seq(), e.recordedAt(), e.eventType(), e.actor(), forgedDetails);
        log.set(4, new AuditChain.Line(e.seq(), e.recordedAt(), e.eventType(), e.actor(), forgedDetails,
                e.prevHash(), forgedHash, e.signature()));
        assertThat(verify(log).problem()).contains("signature invalid");
    }

    @Test
    @DisplayName("SEC-17: a row removed from the middle is detected")
    void removedRowDetected() {
        log.remove(1);
        AuditChain.Verification v = verify(log);
        assertThat(v.ok()).isFalse();
        assertThat(v.problem()).contains("sequence gap");
    }

    @Test
    @DisplayName("SEC-17: rows cut off the end are caught by a published head anchor")
    void truncationCaughtByAnchor() {
        AuditChain.Line published = log.get(4); 
        List<AuditChain.Line> truncated = log.subList(0, 3);
        assertThat(verify(truncated).ok()).isTrue(); 
        AuditChain.Verification v = AuditChain.verify(truncated, key.getPublic(), published.seq(), published.hash());
        assertThat(v.ok()).isFalse(); // ...the anchor can
        assertThat(v.problem()).contains("truncated");
    }

    @Test
    @DisplayName("SEC-17: entries signed with another key are rejected")
    void wrongKeyRejected() {
        AuditChain.Verification v = AuditChain.verify(log, Ed25519Keys.generate().getPublic(), null, null);
        assertThat(v.ok()).isFalse();
        assertThat(v.brokenAt()).isEqualTo(1);
    }
}