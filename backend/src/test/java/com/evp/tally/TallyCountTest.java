package com.evp.tally;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TallyCountTest {

    static final int MEMPHIS = 1, NASHVILLE = 2, CHATTANOOGA = 3, KNOXVILLE = 4;
    static final List<List<Integer>> TENNESSEE = ballots(
            42, List.of(MEMPHIS, NASHVILLE, CHATTANOOGA, KNOXVILLE),
            26, List.of(NASHVILLE, CHATTANOOGA, KNOXVILLE, MEMPHIS),
            15, List.of(CHATTANOOGA, KNOXVILLE, NASHVILLE, MEMPHIS),
            17, List.of(KNOXVILLE, CHATTANOOGA, NASHVILLE, MEMPHIS));
    static final List<TallyDocument.Candidate> CITIES = List.of(
            new TallyDocument.Candidate(MEMPHIS, "Memphis"), new TallyDocument.Candidate(NASHVILLE, "Nashville"),
            new TallyDocument.Candidate(CHATTANOOGA, "Chattanooga"), new TallyDocument.Candidate(KNOXVILLE, "Knoxville"));

    static final int ORANGE = 1, PEAR = 2, CHOC = 3, STRAW = 4, SWEETS = 5;
    static final List<List<Integer>> DESSERTS = ballots(
            4, List.of(ORANGE),
            2, List.of(PEAR, ORANGE),
            8, List.of(CHOC, STRAW),
            4, List.of(CHOC, SWEETS),
            1, List.of(STRAW),
            1, List.of(SWEETS));
    static final List<TallyDocument.Candidate> DESSERT_NAMES = List.of(
            new TallyDocument.Candidate(ORANGE, "Orange"), new TallyDocument.Candidate(PEAR, "Pear"),
            new TallyDocument.Candidate(CHOC, "Chocolate"), new TallyDocument.Candidate(STRAW, "Strawberry"),
            new TallyDocument.Candidate(SWEETS, "Sweets"));

    @SuppressWarnings("unchecked")
    static List<List<Integer>> ballots(Object... countThenPreferences) {
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < countThenPreferences.length; i += 2) {
            out.addAll(Collections.nCopies((Integer) countThenPreferences[i], (List<Integer>) countThenPreferences[i + 1]));
        }
        return out;
    }

    static List<String> receipts(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(String.format("%032x", i));
        }
        return out;
    }

    static ObjectNode publishedSenate() {
        return TallyDocument.senate("VIC", DESSERT_NAMES, DESSERTS, 3, receipts(20), List.of(), List.of(), 0);
    }

    @Test
    @DisplayName("SEC-16: IRV reproduces the Tennessee worked example (Knoxville wins 58-42)")
    void irvTennessee() {
        HouseCount.HouseResult r = HouseCount.count(List.of(MEMPHIS, NASHVILLE, CHATTANOOGA, KNOXVILLE), TENNESSEE, 0);
        assertThat(r.rounds().get(0).get("tallies")).isEqualTo(Map.of("1", 42, "2", 26, "3", 15, "4", 17));
        assertThat(r.rounds().get(0).get("excluded")).isEqualTo(CHATTANOOGA);
        assertThat(r.rounds().get(1).get("excluded")).isEqualTo(NASHVILLE);
        assertThat(r.winner()).isEqualTo(KNOXVILLE);
        assertThat(r.tcp()).isEqualTo(Map.of("1", 42, "4", 58));
    }

    @Test
    @DisplayName("SEC-16: STV reproduces the 3-seat worked example (Chocolate, Orange, Strawberry)")
    void stvThreeSeats() {
        SenateCount.SenateResult r = SenateCount.count(List.of(ORANGE, PEAR, CHOC, STRAW, SWEETS), DESSERTS, 3,
                List.of(), 0);
        assertThat(r.quota()).isEqualTo(6);
        assertThat(r.elected()).containsExactly(CHOC, ORANGE, STRAW);
        Map<String, Object> surplus = r.rounds().stream()
                .filter(round -> "surplus transfer".equals(round.get("action"))).findFirst().orElseThrow();
        assertThat(surplus.get("transferValue")).isEqualTo(0.5);
        assertThat(surplus.get("tallies")).isEqualTo(Map.of("1", 4.0, "2", 2.0, "4", 5.0, "5", 3.0));
    }

    @Test
    @DisplayName("SEC-16: an exclusion tie is broken by the earlier count")
    void tieBrokenByEarlierCount() {
        List<List<Integer>> b = ballots(5, List.of(1), 3, List.of(2), 2, List.of(3), 1, List.of(4, 3));
        HouseCount.HouseResult r = HouseCount.count(List.of(1, 2, 3, 4), b, 0);
        assertThat(r.rounds().get(0).get("excluded")).isEqualTo(4);
        assertThat(r.rounds().get(1).get("excluded")).isEqualTo(3);
        assertThat(r.rounds().get(1).get("rule")).isEqualTo("earlier count");
    }

    @Test
    @DisplayName("SEC-16: exact fractions - no rounding drift in transfer values")
    void fractionsAreExact() {
        Fraction third = Fraction.of(1).dividedBy(Fraction.of(3));
        assertThat(third.plus(third).plus(third)).isEqualTo(Fraction.ONE);
    }

    @Test
    @DisplayName("SEC-16: recount with Chocolate excluded elects Strawberry, Orange, Sweets")
    void recountWithExclusion() {
        SenateCount.SenateResult r = SenateCount.count(List.of(ORANGE, PEAR, CHOC, STRAW, SWEETS), DESSERTS, 3,
                List.of(CHOC), 0);
        // Without Chocolate its 12 ballots go straight to Strawberry (9) and Sweets (5).
        assertThat(r.elected()).containsExactly(STRAW, ORANGE, SWEETS);
        assertThat(r.manuallyExcluded()).containsExactly(CHOC);
    }


    @Test
    @DisplayName("SEC-16: a published House result recomputes to the same result")
    void publishedHouseRecomputes() {
        ObjectNode doc = TallyDocument.house("TENN", CITIES, TENNESSEE, receipts(100), 0);
        assertThat(TallyVerifier.problems(doc)).isEmpty();
    }

    @Test
    @DisplayName("SEC-16: a published Senate result recomputes after a JSON download round trip")
    void publishedSenateRecomputesFromJson() throws Exception {
        JsonNode downloaded = TallyDocument.JSON.readTree(publishedSenate().toString());
        assertThat(TallyVerifier.problems(downloaded)).isEmpty();
    }

    @Test
    @DisplayName("SEC-16: a changed winner is detected")
    void changedWinnerDetected() {
        ObjectNode forged = publishedSenate();
        forged.set("elected", TallyDocument.JSON.valueToTree(List.of(STRAW, ORANGE, SWEETS)));
        assertThat(TallyVerifier.problems(forged)).anyMatch(p -> p.contains("elected differ"));
    }

    @Test
    @DisplayName("SEC-16: a removed ballot is detected")
    void removedBallotDetected() {
        ObjectNode forged = publishedSenate();
        ((ArrayNode) forged.get("ballots")).remove(0);
        assertThat(TallyVerifier.problems(forged)).isNotEmpty();
    }

    @Test
    @DisplayName("SEC-16: a removed receipt is detected by the Merkle root")
    void removedReceiptDetected() {
        ObjectNode forged = publishedSenate();
        ((ArrayNode) forged.get("ballotBox").get("receipts")).remove(0);
        assertThat(TallyVerifier.problems(forged)).anyMatch(p -> p.contains("Merkle root"));
    }

    @Test
    @DisplayName("SEC-16: a secret exclusion without a court order is detected")
    void secretExclusionDetected() {
        ObjectNode forged = publishedSenate();
        ((ObjectNode) forged.get("countInputs")).set("excluded", TallyDocument.JSON.valueToTree(List.of(CHOC)));
        assertThat(TallyVerifier.problems(forged)).anyMatch(p -> p.contains("court order"));
    }

    @Test
    @DisplayName("SEC-16: ballots are published sorted and the Merkle root ignores order")
    void orderRevealsNothing() {
        ObjectNode doc = TallyDocument.house("TENN", CITIES, TENNESSEE, receipts(100), 0);
        assertThat(doc.get("ballots").get(0).toString()).isEqualTo("[1,2,3,4]");
        List<String> reversed = new ArrayList<>(receipts(5));
        Collections.reverse(reversed);
        assertThat(MerkleRoot.of(reversed)).isEqualTo(MerkleRoot.of(receipts(5)));
    }
}