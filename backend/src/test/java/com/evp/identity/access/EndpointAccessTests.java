package com.evp.identity.access;

import com.evp.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(EndpointAccessTests.ProbeConfig.class)
class EndpointAccessTests extends ApiTestBase {
    @TestConfiguration static class ProbeConfig {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }
    @RestController static class ProbeController {
        @GetMapping("/api/unannotated-test")
        public String unannotated() { throw new AssertionError("Unprotected handler was executed"); }
    }
    @Autowired ProbeController probe;

    @ParameterizedTest
    @ValueSource(strings = {"VOTER", "AEC_OFFICER", "DELEGATE", "ADMIN", "AUDITOR"})
    void unannotatedEndpointAndUnknownMethodAreDeniedForEveryRole(String role) throws Exception {
        mvc.perform(get("/api/unannotated-test").with(user("subject", role))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/candidates").with(user("subject", role))).andExpect(status().isForbidden());
    }
    @Test void missingMethodPolicyIsAlsoRejectedByAdvisor() {
        assertThatThrownBy(() -> probe.unannotated()).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest
    @ValueSource(strings = {"VOTER", "AEC_OFFICER", "DELEGATE", "ADMIN", "AUDITOR"})
    void signedInRolesCanReadCandidatesAndIdentity(String role) throws Exception {
        mvc.perform(get("/api/candidates?division=MELBOURNE").with(user("subject", role)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/me").with(user("subject", role))).andExpect(status().isOk());
    }
    @ParameterizedTest
    @ValueSource(strings = {"VOTER", "AEC_OFFICER", "ADMIN"})
    void otherRolesCannotReadDelegateAuditData(String role) throws Exception {
        mvc.perform(get("/api/change-requests").with(user("subject", role))).andExpect(status().isForbidden());
    }
    @ParameterizedTest
    @ValueSource(strings = {"DELEGATE", "AUDITOR"})
    void delegateAndAuditorCanReadChanges(String role) throws Exception {
        mvc.perform(get("/api/change-requests").with(user("subject", role))).andExpect(status().isOk());
    }
    @ParameterizedTest
    @ValueSource(strings = {"VOTER", "AEC_OFFICER", "ADMIN", "AUDITOR"})
    void nonDelegatesCannotProposeApproveOrReject(String role) throws Exception {
        mvc.perform(post("/api/change-requests").with(user("subject", role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"CANDIDATE_DETAILS\",\"payload\":\"{}\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/change-requests/999999/approvals").with(user("subject", role)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/change-requests/999999/rejection").with(user("subject", role)))
            .andExpect(status().isForbidden());
    }
    @ParameterizedTest
    @ValueSource(strings = {"AEC_OFFICER", "DELEGATE", "ADMIN", "AUDITOR"})
    void nonVotersCannotIssueTokensOrPrepareCast(String role) throws Exception {
        mvc.perform(post("/api/tokens").with(user("subject", role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"division\":\"MELBOURNE\",\"blindedMessage\":\"AQ==\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/session/prepare-cast").with(user("subject", role)))
            .andExpect(status().isForbidden());
    }
    @ParameterizedTest
    @ValueSource(strings = {"VOTER", "AEC_OFFICER", "DELEGATE", "AUDITOR"})
    void otherRolesCannotUseAdminEndpoint(String role) throws Exception {
        mvc.perform(get("/api/admin/elevation").with(user("subject", role))).andExpect(status().isForbidden());
    }
    @Test void adminAuthorityWithoutGrantIsNotEnough() throws Exception {
        mvc.perform(get("/api/admin/elevation").with(user("subject", "ADMIN"))).andExpect(status().isForbidden());
    }
}
