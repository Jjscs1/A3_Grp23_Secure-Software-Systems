package com.evp.tally;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.evp.changerequest.ChangeRequestRepository;
import com.evp.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class TallyApiTest extends ApiTestBase {

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublishedTallyRepository tallies;

    @Autowired
    private ChangeRequestRepository changeRequests;

    @BeforeEach
    void clearTables() {
        tallies.deleteAll();
        changeRequests.deleteAll();
    }

    private long publishDessertSenate() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("region", "VIC");
        body.put("candidates", TallyCountTest.DESSERT_NAMES);
        body.put("ballots", TallyCountTest.DESSERTS);
        body.put("vacancies", 3);
        body.put("receipts", TallyCountTest.receipts(20));
        String response = mvc.perform(post("/api/tally/senate").with(user("admin1", "ELECTION_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long proposeExclusion(int candidateId, String... approvers) throws Exception {
        Map<String, Object> body = Map.of(
                "type", "RECOUNT_EXCLUSION",
                "payload", "{\"division\":\"VIC\",\"candidateId\":" + candidateId + "}",
                "courtOrderReference", "HCA 3/2026");
        String response = mvc.perform(post("/api/change-requests").with(user("alice", "DELEGATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        for (String approver : approvers) {
            mvc.perform(post("/api/change-requests/{id}/approvals", id).with(user(approver, "DELEGATE")))
                    .andExpect(status().isOk());
        }
        return id;
    }

    @Test
    @DisplayName("SEC-16: a recount with an approved exclusion gives the expected result")
    void recountWithApprovedExclusion() throws Exception {
        long original = publishDessertSenate();
        mvc.perform(get("/api/public/tally/{id}", original))
                .andExpect(jsonPath("$.elected").value(org.hamcrest.Matchers.contains(3, 1, 4)));

        proposeExclusion(TallyCountTest.CHOC, "bob", "carol");

        String response = mvc.perform(post("/api/tally/{id}/recount", original).with(user("admin1", "ELECTION_ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recountOf").value(original))
                .andReturn().getResponse().getContentAsString();
        long recount = json.readTree(response).get("id").asLong();

        mvc.perform(get("/api/public/tally/{id}", recount))
                .andExpect(jsonPath("$.elected").value(org.hamcrest.Matchers.contains(4, 1, 5)))
                .andExpect(jsonPath("$.manuallyExcluded[0]").value(TallyCountTest.CHOC))
                .andExpect(jsonPath("$.recountOrders[0].courtReference").value("HCA 3/2026"))
                .andExpect(jsonPath("$.recountOrders[0].approvedBy").value(org.hamcrest.Matchers.contains("bob", "carol")));
        mvc.perform(get("/api/public/tally/{id}/verify", recount)).andExpect(jsonPath("$.verified").value(true));
        assertThat(audit.types()).contains("TALLY_PUBLISHED", "RECOUNT_PUBLISHED");
    }

    @Test
    @DisplayName("SEC-16: an exclusion with only one approval is NOT applied to the recount")
    void pendingExclusionIgnored() throws Exception {
        long original = publishDessertSenate();
        proposeExclusion(TallyCountTest.CHOC, "bob"); 

        mvc.perform(post("/api/tally/{id}/recount", original).with(user("admin1", "ELECTION_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TALLY_REFUSED"));
        assertThat(tallies.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-16: published data recomputes to the same result - anyone, no login")
    void publicCanVerifyWithoutLogin() throws Exception {
        long id = publishDessertSenate();
        mvc.perform(get("/api/public/tally")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/public/tally/{id}/verify", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.problems").isEmpty());
    }

    @Test
    @DisplayName("SEC-16: a result edited in the database no longer verifies")
    void editedResultFailsVerification() throws Exception {
        long id = publishDessertSenate();
        String doc = tallies.findById(id).orElseThrow().getDocument();
        jdbc.update("UPDATE published_tally SET document = ? WHERE id = ?",
                doc.replace("\"elected\":[3,1,4]", "\"elected\":[4,1,5]"), id);

        mvc.perform(get("/api/public/tally/{id}/verify", id))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.problems[0]").value(org.hamcrest.Matchers.containsString("elected differ")));
    }

    @Test
    @DisplayName("SEC-16: only an election admin can publish, and informal ballots are refused")
    void publishingIsGuarded() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("region", "TENN");
        body.put("candidates", TallyCountTest.CITIES);
        body.put("ballots", List.of(List.of(1, 1, 2))); 
        body.put("receipts", List.of("r1"));
        mvc.perform(post("/api/tally/house").with(user("voter1", "VOTER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tally/house").with(user("admin1", "ELECTION_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}