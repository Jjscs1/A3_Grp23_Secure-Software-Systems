package com.evp.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evp.identity.access.AdminElevation;
import com.evp.support.ApiTestBase;
import com.evp.tally.PublishedTally;
import com.evp.tally.PublishedTallyRepository;
import com.evp.tally.TallyDocument;
import com.evp.tally.TallyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * SEC-18 acceptance criteria end to end: the commission publishes through the API, and a
 * media outlet fetches the public key and the publications WITHOUT logging in and checks
 * them with the sample verifier.
 */
class ResultsApiTest extends ApiTestBase {

    /** An ADMIN with an active SEC-03 elevation grant (started a minute ago, ends in an hour). */
    private static JwtRequestPostProcessor elevatedAdmin() {
        long now = Instant.now().getEpochSecond();
        return jwt().jwt(j -> j.subject("admin1")
                        .claim(AdminElevation.START, now - 60)
                        .claim(AdminElevation.END, now + 3600))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Autowired
    private ObjectMapper json;

    @Autowired
    private TallyService tallies;

    @Autowired
    private PublishedTallyRepository tallyRows;

    @Autowired
    private ResultsPublicationRepository publications;

    private long tallyId;

    @BeforeEach
    void countedDivision() {
        publications.deleteAll();
        tallyRows.deleteAll();
        PublishedTally t = tallies.publishHouse("MELBOURNE",
                List.of(new TallyDocument.Candidate(1, "NGUYEN, Ava"), new TallyDocument.Candidate(2, "SINGH, Maya")),
                List.of(List.of(1, 2), List.of(1, 2), List.of(2, 1)), List.of("r1", "r2", "r3"), 0, "admin1");
        tallyId = t.getId();
    }

    private JsonNode publish() throws Exception {
        String body = mvc.perform(post("/api/results/{e}/publish", 1).with(elevatedAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tallyIds", List.of(tallyId)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode fetch(String path, Object... vars) throws Exception {
        return json.readTree(mvc.perform(get(path, vars)) // no .with(user(...)): anonymous media outlet
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("SEC-18: the results payload signature verifies with the published public key")
    void signatureVerifiesWithPublishedKey() throws Exception {
        publish();
        String publicKeyPem = fetch("/api/public/results/public-key").get("publicKeyPem").asText();
        JsonNode latest = fetch("/api/public/results/{e}/latest", 1);

        JsonNode payload = new SampleResultsVerifier(publicKeyPem, null).verify(latest, Instant.now());
        assertThat(payload.get("sequence").asLong()).isEqualTo(1);
        assertThat(payload.get("issuedAt").asText()).isNotBlank();
        assertThat(payload.at("/results/0/elected/0/name").asText()).isEqualTo("NGUYEN, Ava");
        assertThat(payload.at("/results/0/documentSha256").asText()).hasSize(64);
        assertThat(payload.has("auditHead")).isTrue();
        assertThat(audit.types()).contains("RESULTS_PUBLISHED");
    }

    @Test
    @DisplayName("SEC-18: a payload with a lower sequence number is rejected by the sample verifier")
    void lowerSequenceRejected() throws Exception {
        publish();
        publish(); // a correction: sequence 2
        String publicKeyPem = fetch("/api/public/results/public-key").get("publicKeyPem").asText();
        assertThat(fetch("/api/public/results/{e}", 1).toString()).isEqualTo("[1,2]");

        SampleResultsVerifier outlet = new SampleResultsVerifier(publicKeyPem, null);
        outlet.verify(fetch("/api/public/results/{e}/latest", 1), Instant.now());
        JsonNode old = fetch("/api/public/results/{e}/{s}", 1, 1); // attacker replays version 1
        assertThatThrownBy(() -> outlet.verify(old, Instant.now()))
                .hasMessageContaining("REPLAY: sequence 1 is lower than the already-accepted sequence 2");
    }

    @Test
    @DisplayName("SEC-18: only an elevated admin can publish; unknown elections are 404")
    void publishingGuarded() throws Exception {
        mvc.perform(post("/api/results/{e}/publish", 1).with(user("voter1", "VOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tallyIds", List.of(tallyId)))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/results/{e}/publish", 1).with(user("admin1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tallyIds", List.of(tallyId)))))
                .andExpect(status().isForbidden()); // ADMIN without an elevation grant
        mvc.perform(get("/api/public/results/{e}/latest", 999)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/results/public-key"))
                .andExpect(jsonPath("$.algorithm").value("Ed25519"))
                .andExpect(jsonPath("$.keyId").isNotEmpty());
    }
}