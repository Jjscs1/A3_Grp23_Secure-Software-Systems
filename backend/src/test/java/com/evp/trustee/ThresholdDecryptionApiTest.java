package com.evp.trustee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.evp.ballot.StoredBallot;
import com.evp.ballot.StoredBallotRepository;
import com.evp.support.ApiTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ThresholdDecryptionApiTest extends ApiTestBase {

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ThresholdDecryptionService service;

    @Autowired
    private ElectionKeyVaultRepository vaults;

    @Autowired
    private PollClosureRepository pollClosures;

    @Autowired
    private StoredBallotRepository ballots;

    private List<String> shares;

    @BeforeEach
    void freshElection() throws Exception {
        vaults.deleteAll();
        pollClosures.deleteAll();
        ballots.deleteAll();

        String body = mvc.perform(post("/api/trustees/key-ceremony").with(user("admin1", "ELECTION_ADMIN")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        shares = new ArrayList<>();
        json.readTree(body).get("shares").forEach(s -> shares.add(s.asText()));

        for (String ballot : List.of("{\"house\":[1,2,3]}", "{\"house\":[2,1,3]}", "{\"house\":[3,1,2]}")) {
            ballots.save(new StoredBallot(BallotEnvelope.encrypt(service.electionPublicKey(), ballot), Instant.now()));
        }
    }

    private ResultActions decrypt(List<String> offered) throws Exception {
        return mvc.perform(post("/api/tally/decrypt")
                .with(user("trustee1", "TRUSTEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("shares", offered))));
    }

    private void closePoll() throws Exception {
        mvc.perform(post("/api/poll/close").with(user("admin1", "ELECTION_ADMIN"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEC-15: decryption before the poll closes returns 423 Locked, even with every share")
    void decryptionBeforeCloseIsLocked() throws Exception {
        decrypt(shares)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("POLL_STILL_OPEN"));
        assertThat(audit.types()).contains("DECRYPTION_REFUSED");
    }

    @Test
    @DisplayName("SEC-15: 2 shares fail")
    void twoSharesFail() throws Exception {
        closePoll();
        decrypt(shares.subList(0, 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SHARES"))
                .andExpect(jsonPath("$.ballots").doesNotExist());
    }

    @Test
    @DisplayName("SEC-15: 3 of 5 shares succeed and decrypt every ballot")
    void threeOfFiveSucceed() throws Exception {
        closePoll();
        decrypt(List.of(shares.get(0), shares.get(2), shares.get(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ballots.length()").value(3))
                .andExpect(jsonPath("$.ballots[0]").value("{\"house\":[1,2,3]}"))
                .andExpect(jsonPath("$.undecryptable").value(0));
        assertThat(audit.types()).contains("KEY_CEREMONY_HELD", "POLL_CLOSED", "BALLOT_BOX_DECRYPTED");
    }

    @Test
    @DisplayName("SEC-15: a different set of 3 shares works too")
    void anotherThreeSucceed() throws Exception {
        closePoll();
        decrypt(shares.subList(2, 5)).andExpect(status().isOk()).andExpect(jsonPath("$.ballots.length()").value(3));
    }

    @Test
    @DisplayName("SEC-15: the same share sent three times does not count as three trustees")
    void repeatedShareFails() throws Exception {
        closePoll();
        decrypt(List.of(shares.get(1), shares.get(1), shares.get(1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SHARES"));
    }

    @Test
    @DisplayName("SEC-15: shares from a different key ceremony do not unlock this election's key")
    void foreignSharesFail() throws Exception {
        closePoll();
        List<String> foreign = Shamir.split(new byte[32], 3, 5);
        decrypt(foreign.subList(0, 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INVALID_SHARES"));
    }

    @Test
    @DisplayName("SEC-15: only a trustee may call decrypt, and the ceremony cannot be re-run")
    void rolesAndCeremonyGuarded() throws Exception {
        closePoll();
        mvc.perform(post("/api/tally/decrypt").with(user("voter1", "VOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("shares", shares))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trustees/key-ceremony").with(user("admin1", "ELECTION_ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("SEC-15: the database holds only the LOCKED private key, never the key itself")
    void privateKeyIsStoredLocked() throws Exception {
        ElectionKeyVault vault = vaults.findAll().get(0);
        assertThat(vault.getLockedPrivateKey()).doesNotStartWith("MII");
        mvc.perform(get("/api/trustees/election-key").with(user("voter1", "VOTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value(BallotEnvelope.ALGORITHM));
    }
}