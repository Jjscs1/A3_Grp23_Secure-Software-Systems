package com.evp.identity;

import com.evp.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAccessTests extends ApiTestBase {

    @Test
    void voterCannotReadChangeRequests() throws Exception {
        mvc.perform(get("/api/change-requests")
                .with(user("voter1", "VOTER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void voterCannotApproveChangeRequests() throws Exception {
        mvc.perform(post("/api/change-requests/999999/approvals")
                .with(user("voter1", "VOTER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void voterCannotRejectChangeRequests() throws Exception {
        mvc.perform(post("/api/change-requests/999999/rejection")
                .with(user("voter1", "VOTER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditorCanReadChangeRequests() throws Exception {
        mvc.perform(get("/api/change-requests")
                .with(user("auditor1", "AUDITOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void auditorCannotModifyChangeRequests() throws Exception {
        mvc.perform(post("/api/change-requests")
                .with(user("auditor1", "AUDITOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "CANDIDATE_DETAILS",
                      "payload": "{\\"division\\":\\"MELBOURNE\\",\\"name\\":\\"Test Candidate\\",\\"party\\":\\"Independent\\"}"
                    }
                    """))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/change-requests/999999/approvals")
                .with(user("auditor1", "AUDITOR")))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/change-requests/999999/rejection")
                .with(user("auditor1", "AUDITOR")))
            .andExpect(status().isForbidden());
    }
}