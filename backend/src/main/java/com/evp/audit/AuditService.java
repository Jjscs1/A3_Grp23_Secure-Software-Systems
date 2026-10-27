package com.evp.audit;
import com.evp.security.Ed25519Keys;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEntryRepository entries;
    private final AuditSigningConfig.AuditSigningKey key;

    public AuditService(AuditEntryRepository entries, AuditSigningConfig.AuditSigningKey key) {
        this.entries = entries;
        this.key = key;
    }

    public record Export(String publicKeyPem, List<AuditChain.Line> entries) {
    }

    public record Head(long seq, String hash) {
    }

    @Transactional(readOnly = true)
    public Export export() {
        return new Export(publicKeyPem(), lines());
    }

    @Transactional(readOnly = true)
    public AuditChain.Verification verify(Long anchorSeq, String anchorHash) {
        return AuditChain.verify(lines(), key.keyPair().getPublic(), anchorSeq, anchorHash);
    }

    @Transactional(readOnly = true)
    public Head head() {
        return entries.findTopByOrderBySeqDesc()
                .map(e -> new Head(e.getSeq(), e.getHash()))
                .orElse(new Head(0, AuditChain.GENESIS));
    }

    public String publicKeyPem() {
        return Ed25519Keys.publicKeyPem(key.keyPair().getPublic());
    }

    private List<AuditChain.Line> lines() {
        return entries.findAllByOrderBySeqAsc().stream().map(AuditChain.Line::of).toList();
    }
}