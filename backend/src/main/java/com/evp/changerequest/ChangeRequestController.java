package com.evp.changerequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SEC-05 REST API. Identity always comes from the login token, never from the request body. */
@RestController
@RequestMapping("/api")
public class ChangeRequestController {

    private final ChangeRequestService service;
    private final CandidateRepository candidates;

    public ChangeRequestController(ChangeRequestService service, CandidateRepository candidates) {
        this.service = service;
        this.candidates = candidates;
    }

    public record ProposeRequest(
            @NotNull ChangeRequestType type,
            @NotBlank @Size(max = 4000) String payload,
            @Size(max = 64) String courtOrderReference,
            @Min(1) @Max(10_000_000) Integer recordCount) {
    }

    public record ChangeRequestView(Long id, ChangeRequestType type, String payload, String proposer,
                                    String courtOrderReference, Integer recordCount, ChangeRequestStatus status,
                                    List<String> approvers, int requiredApprovals, Instant createdAt) {
        static ChangeRequestView of(ChangeRequest cr) {
            return new ChangeRequestView(cr.getId(), cr.getType(), cr.getPayload(), cr.getProposer(),
                    cr.getCourtOrderReference(), cr.getRecordCount(), cr.getStatus(), cr.approverNames(),
                    DualControlPolicy.requiredApprovals(cr.getType()), cr.getCreatedAt());
        }
    }

    public record CandidateView(Long id, String name, String party) {
    }

    @PostMapping("/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DELEGATE')")
    public ChangeRequestView propose(@Valid @RequestBody ProposeRequest body, Principal delegate) {
        return ChangeRequestView.of(service.propose(delegate.getName(), body.type(), body.payload(),
                body.courtOrderReference(), body.recordCount()));
    }

    @PostMapping("/change-requests/{id}/approvals")
    @PreAuthorize("hasRole('DELEGATE')")
    public ChangeRequestView approve(@PathVariable Long id, Principal delegate) {
        return ChangeRequestView.of(service.approve(id, delegate.getName()));
    }

    @PostMapping("/change-requests/{id}/rejection")
    @PreAuthorize("hasRole('DELEGATE')")
    public ChangeRequestView reject(@PathVariable Long id, Principal delegate) {
        return ChangeRequestView.of(service.reject(id, delegate.getName()));
    }

    @GetMapping("/change-requests")
    @PreAuthorize("hasAnyRole('DELEGATE', 'AUDITOR')")
    public List<ChangeRequestView> pending() {
        return service.pending().stream().map(ChangeRequestView::of).toList();
    }

    /** Candidate list for the ballot screen. Any signed-in user. */
    @GetMapping("/candidates")
    @PreAuthorize("isAuthenticated()")
    public List<CandidateView> candidates(@RequestParam String division) {
        return candidates.findByDivisionOrderByNameAsc(division).stream()
                .map(c -> new CandidateView(c.getId(), c.getName(), c.getParty()))
                .toList();
    }
}
