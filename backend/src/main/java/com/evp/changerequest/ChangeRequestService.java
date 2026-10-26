package com.evp.changerequest;

import com.evp.audit.AuditLogger;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEC-05: four-eyes workflow. Propose, approve by a different delegate (two different
 * delegates for a recount exclusion), then apply. Every step, including refused ones,
 * goes to the audit log (SEC-17).
 */
@Service
public class ChangeRequestService {

    private final ChangeRequestRepository requests;
    private final List<ChangeRequestApplier> appliers;
    private final AuditLogger audit;
    private final Clock clock;

    public ChangeRequestService(ChangeRequestRepository requests, List<ChangeRequestApplier> appliers,
                                AuditLogger audit, Clock clock) {
        this.requests = requests;
        this.appliers = appliers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ChangeRequest propose(String proposer, ChangeRequestType type, String payload,
                                 String courtOrderReference, Integer recordCount) {
        try {
            DualControlPolicy.validateProposal(type, courtOrderReference, recordCount);
            applierFor(type).validate(payload);
        } catch (InvalidChangeRequestException e) {
            audit.record("CHANGE_PROPOSAL_REJECTED", proposer, Map.of("type", type.name(), "reason", e.getMessage()));
            throw e;
        }
        ChangeRequest saved = requests.save(
                ChangeRequest.propose(type, payload, proposer, courtOrderReference, recordCount, clock.instant()));
        audit.record("CHANGE_PROPOSED", proposer, details(saved));
        return saved;
    }

    @Transactional
    public ChangeRequest approve(Long id, String approver) {
        ChangeRequest cr = requests.findById(id).orElseThrow(ChangeRequestNotFoundException::new);
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ChangeRequestStateException("Change request is already " + cr.getStatus());
        }
        try {
            DualControlPolicy.checkApprover(cr.getProposer(), approver, cr.approverNames());
        } catch (SelfApprovalException | DuplicateApprovalException e) {
            Map<String, String> d = details(cr);
            d.put("reason", e.getClass().getSimpleName());
            audit.record("CHANGE_APPROVAL_REFUSED", approver, d);
            throw e;
        }

        cr.addApproval(approver, clock.instant());
        Map<String, String> approved = details(cr);
        approved.put("approvals", String.valueOf(cr.getApprovals().size()));
        audit.record("CHANGE_APPROVED", approver, approved);

        if (DualControlPolicy.isSatisfied(cr.getType(), cr.getApprovals().size())) {
            applierFor(cr.getType()).apply(cr);
            cr.markApplied(clock.instant());
            audit.record("CHANGE_APPLIED", approver, details(cr));
        }
        return requests.saveAndFlush(cr);
    }

    /** Any delegate, including the proposer, may withdraw or reject a pending change. */
    @Transactional
    public ChangeRequest reject(Long id, String actor) {
        ChangeRequest cr = requests.findById(id).orElseThrow(ChangeRequestNotFoundException::new);
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ChangeRequestStateException("Change request is already " + cr.getStatus());
        }
        cr.markRejected(clock.instant());
        audit.record("CHANGE_REJECTED", actor, details(cr));
        return requests.saveAndFlush(cr);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequest> pending() {
        return requests.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING);
    }

    private ChangeRequestApplier applierFor(ChangeRequestType type) {
        return appliers.stream()
                .filter(a -> a.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No applier for " + type));
    }

    private static Map<String, String> details(ChangeRequest cr) {
        Map<String, String> d = new HashMap<>();
        if (cr.getId() != null) {
            d.put("changeRequestId", String.valueOf(cr.getId()));
        }
        d.put("type", cr.getType().name());
        d.put("proposer", cr.getProposer());
        if (cr.getCourtOrderReference() != null) {
            d.put("courtOrderReference", cr.getCourtOrderReference());
        }
        return d;
    }
}
