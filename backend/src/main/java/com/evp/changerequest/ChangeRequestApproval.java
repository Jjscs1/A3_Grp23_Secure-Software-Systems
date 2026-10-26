package com.evp.changerequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.Check;

/**
 * SEC-05: one approval of a change request.
 *
 * Two database rules back up the checks in DualControlPolicy, so they hold even if
 * someone bypasses the service layer:
 *  - CHECK (approver <> proposer): nobody can approve their own change.
 *  - UNIQUE (change_request_id, approver): one approval per delegate per change.
 * The proposer is copied onto each approval row so the CHECK can see it.
 */
@Entity
@Table(name = "change_request_approval",
        uniqueConstraints = @UniqueConstraint(name = "uq_approval_once_per_delegate",
                columnNames = {"change_request_id", "approver"}))
@Check(constraints = "approver <> proposer")
public class ChangeRequestApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "change_request_id", nullable = false)
    private ChangeRequest changeRequest;

    @Column(name = "approver", nullable = false, length = 255)
    private String approver;

    @Column(name = "proposer", nullable = false, length = 255)
    private String proposer;

    @Column(nullable = false)
    private Instant approvedAt;

    protected ChangeRequestApproval() {
        // for JPA
    }

    ChangeRequestApproval(ChangeRequest changeRequest, String approver, String proposer, Instant approvedAt) {
        this.changeRequest = changeRequest;
        this.approver = approver;
        this.proposer = proposer;
        this.approvedAt = approvedAt;
    }

    public String getApprover() {
        return approver;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
