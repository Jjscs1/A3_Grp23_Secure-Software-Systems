package com.evp.trustee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.evp.ballot.StoredBallot;
import com.evp.ballot.StoredBallotRepository;
import com.evp.identity.access.AdminElevation;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
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

    private static JwtRequestPostProcessor elevatedAdmin() {
        long now = Instant.now().getEpochSecond();
        return jwt().jwt(j -> j.subject("admin1")
                        .claim(AdminElevation.START, now - 60)
                        .claim(AdminElevation.END, now + 3600))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @BeforeEach
    void freshElection() throws Exception {
        vaults.deleteAll();
        pollClosures.deleteAll();
        ballots.deleteAll();

        String body = mvc.perform(post("/api/trustees/key-ceremony").with(elevatedAdmin()))
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
                .with(user("trustee1", "AEC_OFFICER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("shares", offered))));
    }

    private void closePoll() throws Exception {
        mvc.perform(post("/api/poll/close").with(elevatedAdmin())).andExpect(status().isOk());
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
    @DisplayName("SEC-15: only an AEC officer may call decrypt, and the ceremony cannot be re-run")
    void rolesAndCeremonyGuarded() throws Exception {
        closePoll();
        mvc.perform(post("/api/tally/decrypt").with(user("voter1", "VOTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("shares", shares))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trustees/key-ceremony").with(elevatedAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("SEC-15: ADMIN without an active elevation grant cannot run the ceremony or close the poll")
    void adminWithoutElevationIsRefused() throws Exception {
        mvc.perform(post("/api/trustees/key-ceremony").with(user("admin1", "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/poll/close").with(user("admin1", "ADMIN")))
                .andExpect(status().isForbidden());
        assertThat(pollClosures.count()).isZero();
    }

    @Test
    @DisplayName("SEC-15: the public key and poll status are readable without logging in")
    void publicEndpointsNeedNoLogin() throws Exception {
        mvc.perform(get("/api/trustees/election-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value(BallotEnvelope.ALGORITHM))
                .andExpect(jsonPath("$.publicKeyPem").value(org.hamcrest.Matchers.startsWith("-----BEGIN PUBLIC KEY-----")));
        mvc.perform(get("/api/poll/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false));
    }

    @Test
    @DisplayName("SEC-15: the database holds only the LOCKED private key, never the key itself")
    void privateKeyIsStoredLocked() {
        ElectionKeyVault vault = vaults.findAll().get(0);
        assertThat(vault.getLockedPrivateKey()).doesNotStartWith("MII"); 
    }
}