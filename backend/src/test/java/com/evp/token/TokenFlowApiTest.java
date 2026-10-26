package com.evp.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evp.ballot.BallotSubmissionService;
import com.evp.ballot.SpentTokenRepository;
import com.evp.ballot.StoredBallotRepository;
import com.evp.enrolment.EnrolmentLookupKey;
import com.evp.support.ApiTestBase;
import com.evp.voter.Voter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * SEC-10 and SEC-11 end to end through the REST API:
 * eligibility check, blind token issuance, anonymous ballot submission, reuse and races.
 */
class TokenFlowApiTest extends ApiTestBase {

    private static final Base64.Encoder B64 = Base64.getEncoder();

    @Autowired
    private BlindSigner signer;

    @Autowired
    private BallotSubmissionService submissions;

    @Autowired
    private EnrolmentLookupKey lookupKey;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private SpentTokenRepository spentTokens;

    @Autowired
    private StoredBallotRepository storedBallots;

    private TestBlindClient client;

    @BeforeEach
    void setUp() throws Exception {
        spentTokens.deleteAll();
        storedBallots.deleteAll();
        RSAPublicKey pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(signer.publicKeySpki()));
        client = new TestBlindClient(pub);
        enrol("voter-1", LocalDate.of(1990, 1, 1), "MELBOURNE");
    }

    private void enrol(String subject, LocalDate dob, String division) {
        voters.save(Voter.enrol(subject, lookupKey.compute(subject, dob, "3000"), dob, true, division));
    }

    private boolean hasVoted(String subject) {
        return voters.findAll().stream()
                .filter(v -> subject.equals(v.getOidcSubject()))
                .findFirst().orElseThrow().hasVoted();
    }

    private ResultActions requestToken(String subject, String division, byte[] blinded) throws Exception {
        return mvc.perform(post("/api/tokens")
                .with(user(subject, "VOTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("division", division, "blindedMessage", B64.encodeToString(blinded)))));
    }

    /** What the browser does: blind a fresh serial, get it signed, unblind. Returns {serial, signature}. */
    private byte[][] obtainToken(String subject) throws Exception {
        byte[] serial = new byte[32];
        new SecureRandom().nextBytes(serial);
        TestBlindClient.Blinded blinded = client.blind(serial);
        String response = requestToken(subject, "MELBOURNE", blinded.blindedMessage())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        byte[] blindSig = Base64.getDecoder().decode(json.readTree(response).get("blindSignature").asText());
        return new byte[][] {serial, client.finalizeSignature(blindSig, blinded.inverse())};
    }

    private ResultActions submitBallot(byte[] serial, byte[] signature) throws Exception {
        return mvc.perform(post("/api/ballots") // no login: submission is anonymous
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "serial", B64.encodeToString(serial),
                        "signature", B64.encodeToString(signature),
                        "encryptedBallot", "{\"alg\":\"test\",\"ct\":\"AAAA\"}"))));
    }

    // ---- SEC-11 ----

    @Test
    @DisplayName("SEC-11: a token signature verifies against the election key")
    void issuedTokenVerifies() throws Exception {
        byte[][] token = obtainToken("voter-1");
        assertThat(signer.verify(token[0], token[1])).isTrue();
    }

    @Test
    @DisplayName("SEC-11: the signer never sees the unblinded serial")
    void serialNeverReachesServer() throws Exception {
        byte[][] token = obtainToken("voter-1");
        String serialB64 = B64.encodeToString(token[0]);
        assertThat(audit.events()).allSatisfy(e ->
                assertThat(e.details().values()).doesNotContain(serialB64));
    }

    @Test
    @DisplayName("SEC-11: has-voted is set in the same transaction as issuance")
    void hasVotedSetOnIssue() throws Exception {
        assertThat(hasVoted("voter-1")).isFalse();
        obtainToken("voter-1");
        assertThat(hasVoted("voter-1")).isTrue();
    }

    @Test
    @DisplayName("SEC-11: if signing fails, has-voted is rolled back")
    void failedIssueRollsBack() throws Exception {
        requestToken("voter-1", "MELBOURNE", new byte[10]).andExpect(status().isBadRequest());
        assertThat(hasVoted("voter-1")).isFalse();
    }

    @Test
    @DisplayName("SEC-11: a second ballot with the same serial returns 409")
    void reusedTokenConflict() throws Exception {
        byte[][] token = obtainToken("voter-1");
        submitBallot(token[0], token[1]).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECORDED"));
        submitBallot(token[0], token[1]).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TOKEN_ALREADY_USED"));
        assertThat(storedBallots.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SEC-11: two concurrent submissions produce one ballot")
    void concurrentSubmissionsProduceOneBallot() throws Exception {
        byte[][] token = obtainToken("voter-1");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        Callable<String> attempt = () -> {
            go.await();
            try {
                submissions.submit(token[0], token[1], "{\"alg\":\"test\",\"ct\":\"AAAA\"}");
                return "ok";
            } catch (DuplicateTokenException e) {
                return "duplicate";
            }
        };
        results.add(pool.submit(attempt));
        results.add(pool.submit(attempt));
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("ok", "duplicate");
        assertThat(storedBallots.count()).isEqualTo(1);
        assertThat(spentTokens.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A forged token is refused and nothing is stored")
    void forgedTokenRefused() throws Exception {
        byte[] serial = new byte[32];
        byte[] fakeSignature = new byte[signer.modulusBytes()];
        new SecureRandom().nextBytes(fakeSignature);
        submitBallot(serial, fakeSignature).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        assertThat(storedBallots.count()).isZero();
    }

    @Test
    @DisplayName("Two simultaneous token requests from one voter: only one succeeds")
    void concurrentIssueOnlyOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> attempt = () -> {
            go.await();
            byte[] serial = new byte[32];
            new SecureRandom().nextBytes(serial);
            return requestToken("voter-1", "MELBOURNE", client.blind(serial).blindedMessage())
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> a = pool.submit(attempt);
        Future<Integer> b = pool.submit(attempt);
        go.countDown();
        List<Integer> statuses = List.of(a.get(), b.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 403);
    }

    @Test
    @DisplayName("Only a signed-in voter can request a token")
    void tokenEndpointNeedsVoterRole() throws Exception {
        mvc.perform(post("/api/tokens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"division\":\"MELBOURNE\",\"blindedMessage\":\"AAAA\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tokens").with(user("delegate-1", "DELEGATE")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"division\":\"MELBOURNE\",\"blindedMessage\":\"AAAA\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- SEC-10 through the API ----

    @Test
    @DisplayName("SEC-10: under 18 is refused and has-voted stays false")
    void under18RefusedViaApi() throws Exception {
        enrol("teen-1", LocalDate.of(2010, 1, 1), "MELBOURNE");
        requestToken("teen-1", "MELBOURNE", client.blind(new byte[32]).blindedMessage())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.detail").value("UNDER_18"));
        assertThat(hasVoted("teen-1")).isFalse();
    }

    @Test
    @DisplayName("SEC-10: wrong division is refused")
    void wrongDivisionViaApi() throws Exception {
        requestToken("voter-1", "WILLS", client.blind(new byte[32]).blindedMessage())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("WRONG_DIVISION"));
        assertThat(hasVoted("voter-1")).isFalse();
    }

    @Test
    @DisplayName("SEC-10: someone not on the roll is refused")
    void notEnrolledViaApi() throws Exception {
        requestToken("stranger", "MELBOURNE", client.blind(new byte[32]).blindedMessage())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("NOT_ENROLLED"));
    }

    @Test
    @DisplayName("SEC-10: already voted is refused")
    void alreadyVotedViaApi() throws Exception {
        obtainToken("voter-1");
        requestToken("voter-1", "MELBOURNE", client.blind(new byte[32]).blindedMessage())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("ALREADY_VOTED"));
    }
}
