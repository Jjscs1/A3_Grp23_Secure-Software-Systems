package com.evp.changerequest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SEC-05: a proposed change that has no effect until enough different delegates approve.
 *
 * {@code @Version} gives optimistic locking: if two delegates approve at the same moment,
 * one commit fails with a conflict (HTTP 409) instead of both applying the change.
 */
@Entity
@Table(name = "change_request")
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChangeRequestType type;

    /** JSON describing the change, validated by the applier for this type. */
    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false, length = 255)
    private String proposer;

    @Column(length = 64)
    private String courtOrderReference;

    private Integer recordCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant decidedAt;

    /** Eager: at most three approvals, and API responses read them outside the transaction. */
    @OneToMany(mappedBy = "changeRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChangeRequestApproval> approvals = new ArrayList<>();

    @Version
    private long version;

    protected ChangeRequest() {
        // for JPA
    }

    public static ChangeRequest propose(ChangeRequestType type, String payload, String proposer,
                                        String courtOrderReference, Integer recordCount, Instant now) {
        ChangeRequest cr = new ChangeRequest();
        cr.type = type;
        cr.payload = payload;
        cr.proposer = proposer;
        cr.courtOrderReference = courtOrderReference;
        cr.recordCount = recordCount;
        cr.status = ChangeRequestStatus.PENDING;
        cr.createdAt = now;
        return cr;
    }

    void addApproval(String approver, Instant now) {
        approvals.add(new ChangeRequestApproval(this, approver, proposer, now));
    }

    void markApplied(Instant now) {
        this.status = ChangeRequestStatus.APPLIED;
        this.decidedAt = now;
    }

    void markRejected(Instant now) {
        this.status = ChangeRequestStatus.REJECTED;
        this.decidedAt = now;
    }

    public List<String> approverNames() {
        return approvals.stream().map(ChangeRequestApproval::getApprover).toList();
    }

    public Long getId() {
        return id;
    }

    public ChangeRequestType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getProposer() {
        return proposer;
    }

    public String getCourtOrderReference() {
        return courtOrderReference;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public ChangeRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public List<ChangeRequestApproval> getApprovals() {
        return Collections.unmodifiableList(approvals);
    }
}
