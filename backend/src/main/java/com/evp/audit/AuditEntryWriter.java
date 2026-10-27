package com.evp.audit;
import com.evp.security.Ed25519Keys;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditEntryWriter {

    private final AuditEntryRepository entries;
    private final AuditSigningConfig.AuditSigningKey key;
    private final Clock clock;

    public AuditEntryWriter(AuditEntryRepository entries, AuditSigningConfig.AuditSigningKey key, Clock clock) {
        this.entries = entries;
        this.key = key;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEntry append(String eventType, String actor, Map<String, String> details) {
        AuditEntry last = entries.findTopByOrderBySeqDesc().orElse(null);
        long seq = last == null ? 1 : last.getSeq() + 1;
        String prevHash = last == null ? AuditChain.GENESIS : last.getHash();
        String recordedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS).toString();
        String canonicalDetails = AuditChain.canonicalDetails(details);
        String safeActor = actor == null || actor.isBlank() ? "anonymous" : actor;

        String hash = AuditChain.hash(prevHash, seq, recordedAt, eventType, safeActor, canonicalDetails);
        String signature = Base64.getEncoder().encodeToString(
                Ed25519Keys.sign(key.keyPair().getPrivate(), HexFormat.of().parseHex(hash)));
        return entries.saveAndFlush(new AuditEntry(seq, recordedAt, eventType, safeActor, canonicalDetails,
                prevHash, hash, signature));
    }
}