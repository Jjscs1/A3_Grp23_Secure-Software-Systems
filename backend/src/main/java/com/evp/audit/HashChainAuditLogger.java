package com.evp.audit;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class HashChainAuditLogger implements AuditLogger {

    private final AuditEntryWriter writer;

    public HashChainAuditLogger(AuditEntryWriter writer) {
        this.writer = writer;
    }

    @Override
    public synchronized void record(String eventType, String actor, Map<String, String> details) {
        try {
            writer.append(eventType, actor, details);
        } catch (DataIntegrityViolationException anotherServerWroteThisSeq) {
            writer.append(eventType, actor, details);
        }
    }
}