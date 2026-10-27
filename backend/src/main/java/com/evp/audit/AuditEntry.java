package com.evp.audit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "audit_entry", uniqueConstraints = @UniqueConstraint(name = "uq_audit_entry_seq", columnNames = "seq"))
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long seq;

    @Column(nullable = false, length = 40)
    private String recordedAt;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(nullable = false, length = 4000)
    private String details;

    @Column(nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false, length = 120)
    private String signature;

    protected AuditEntry() {
        // for JPA
    }

    AuditEntry(long seq, String recordedAt, String eventType, String actor, String details,
               String prevHash, String hash, String signature) {
        this.seq = seq;
        this.recordedAt = recordedAt;
        this.eventType = eventType;
        this.actor = actor;
        this.details = details;
        this.prevHash = prevHash;
        this.hash = hash;
        this.signature = signature;
    }

    public long getSeq() {
        return seq;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }

    public String getSignature() {
        return signature;
    }
}