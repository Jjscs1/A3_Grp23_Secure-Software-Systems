package com.evp.changerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evp.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** SEC-05 acceptance criteria through the REST API, with real roles and a real database. */
class ChangeRequestApiTest extends ApiTestBase {

    private static final String CANDIDATE = "{\"division\":\"MELBOURNE\",\"name\":\"Jane Citizen\",\"party\":\"Independent\"}";
    private static final String EXCLUSION = "{\"division\":\"MELBOURNE\",\"candidateId\":7}";

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChangeRequestRepository changeRequests;

    @Autowired
    private CandidateRepository candidates;

    @BeforeEach
    void clearTables() {
        changeRequests.deleteAll();
        candidates.deleteAll();
    }

    private ResultActions propose(String delegate, String type, String payload, String courtOrder) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("payload", payload);
        if (courtOrder != null) {
            body.put("courtOrderReference", courtOrder);
        }
        return mvc.perform(post("/api/change-requests")
                .with(user(delegate, "DELEGATE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private long proposedId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions approve(long id, String delegate) throws Exception {
        return mvc.perform(post("/api/change-requests/{id}/approvals", id).with(user(delegate, "DELEGATE")));
    }

    @Test
    @DisplayName("SEC-05: a proposer approving their own change returns 403")
    void selfApprovalForbidden() throws Exception {
        long id = proposedId(propose("alice", "CANDIDATE_DETAILS", CANDIDATE, null).andExpect(status().isCreated()));

        approve(id, "alice")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SELF_APPROVAL_FORBIDDEN"));

        assertThat(changeRequests.findById(id).orElseThrow().getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        assertThat(candidates.count()).isZero();
    }

    @Test
    @DisplayName("SEC-05: a different delegate's approval applies the change")
    void differentDelegateApproves() throws Exception {
        long id = proposedId(propose("alice", "CANDIDATE_DETAILS", CANDIDATE, null));

        approve(id, "bob")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.approvers[0]").value("bob"));

        assertThat(candidates.findByDivisionOrderByNameAsc("MELBOURNE"))
                .extracting(Candidate::getName).containsExactly("Jane Citizen");
    }

    @Test
    @DisplayName("SEC-05: an exclusion with 1 approval is not applied")
    void exclusionNeedsTwoApprovals() throws Exception {
        long id = proposedId(propose("alice", "RECOUNT_EXCLUSION", EXCLUSION, "CDR 12/2026").andExpect(status().isCreated()));

        approve(id, "bob")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requiredApprovals").value(2));

        approve(id, "carol")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    @DisplayName("SEC-05: an exclusion with no court-order reference is not applied")
    void exclusionWithoutCourtOrderRejected() throws Exception {
        propose("alice", "RECOUNT_EXCLUSION", EXCLUSION, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_CHANGE_REQUEST"));

        assertThat(changeRequests.count()).isZero();
    }

    @Test
    @DisplayName("SEC-05: every step appears in the audit log")
    void everyStepAudited() throws Exception {
        long id = proposedId(propose("alice", "CANDIDATE_DETAILS", CANDIDATE, null));
        approve(id, "alice"); // refused
        approve(id, "bob");   // approved and applied

        assertThat(audit.types()).containsExactly(
                "CHANGE_PROPOSED", "CHANGE_APPROVAL_REFUSED", "CHANGE_APPROVED", "CHANGE_APPLIED");
        assertThat(audit.events().get(1).actor()).isEqualTo("alice");
        assertThat(audit.events().get(2).actor()).isEqualTo("bob");
    }

    @Test
    @DisplayName("The same delegate cannot approve twice")
    void duplicateApprovalConflict() throws Exception {
        long id = proposedId(propose("alice", "RECOUNT_EXCLUSION", EXCLUSION, "CDR 12/2026"));
        approve(id, "bob").andExpect(status().isOk());
        approve(id, "bob").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A voter cannot propose a change")
    void nonDelegateForbidden() throws Exception {
        mvc.perform(post("/api/change-requests")
                        .with(user("mallory", "VOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CANDIDATE_DETAILS\",\"payload\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid candidate payload is rejected at proposal time")
    void invalidPayloadRejected() throws Exception {
        propose("alice", "CANDIDATE_DETAILS", "{\"division\":\"MELBOURNE\",\"name\":\"<script>alert(1)</script>\"}", null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("The database itself refuses a self-approval row, even bypassing the service")
    void databaseCheckConstraint() {
        ChangeRequest cr = changeRequests.save(ChangeRequest.propose(
                ChangeRequestType.CANDIDATE_DETAILS, CANDIDATE, "alice", null, null, Instant.now()));

        assertThatThrownBy(() -> jdbc.update(
                "insert into change_request_approval (change_request_id, approver, proposer, approved_at) values (?, ?, ?, ?)",
                cr.getId(), "alice", "alice", java.sql.Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
