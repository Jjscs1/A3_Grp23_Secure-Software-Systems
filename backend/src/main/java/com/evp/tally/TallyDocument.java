package com.evp.tally;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TallyDocument {

    static final ObjectMapper JSON = new ObjectMapper();

    public record Candidate(int id, String name) {
    }

    private TallyDocument() {
    }

    public static ObjectNode house(String region, List<Candidate> candidates, List<List<Integer>> ballots,
                                   List<String> receipts, long seed) {
        List<Integer> ids = candidates.stream().map(Candidate::id).toList();
        HouseCount.HouseResult outcome = HouseCount.count(ids, ballots, seed);

        ObjectNode doc = header("house", region,
                "full preferential (instant run-off), distributed to two candidates", candidates);
        ObjectNode inputs = doc.putObject("countInputs");
        inputs.set("candidateIds", JSON.valueToTree(ids));
        inputs.putArray("excluded");
        inputs.put("tiebreakSeed", seed);
        doc.putArray("recountOrders");
        doc.set("ballots", JSON.valueToTree(sorted(ballots)));
        doc.set("ballotBox", ballotBox(receipts));

        doc.put("winner", outcome.winner());
        doc.put("formal", outcome.formal());
        doc.set("tcp", JSON.valueToTree(outcome.tcp()));
        doc.set("majorityRound", JSON.valueToTree(outcome.majorityRound()));
        doc.set("rounds", JSON.valueToTree(outcome.rounds()));
        return doc;
    }

    public static ObjectNode senate(String region, List<Candidate> candidates, List<List<Integer>> ballots,
                                    int vacancies, List<String> receipts, List<Integer> excluded,
                                    List<Map<String, Object>> recountOrders, long seed) {
        List<Integer> ids = candidates.stream().map(Candidate::id).toList();
        SenateCount.SenateResult outcome = SenateCount.count(ids, ballots, vacancies, excluded, seed);

        ObjectNode doc = header("senate", region,
                "single transferable vote, Droop quota, weighted surplus transfer", candidates);
        ObjectNode inputs = doc.putObject("countInputs");
        inputs.set("candidateIds", JSON.valueToTree(ids));
        inputs.put("vacancies", vacancies);
        inputs.set("excluded", JSON.valueToTree(excluded.stream().sorted().toList()));
        inputs.put("tiebreakSeed", seed);
        doc.set("recountOrders", JSON.valueToTree(recountOrders));
        doc.set("ballots", JSON.valueToTree(sorted(ballots)));
        doc.set("ballotBox", ballotBox(receipts));

        doc.put("formal", outcome.formal());
        doc.put("quota", outcome.quota());
        doc.set("elected", JSON.valueToTree(outcome.elected()));
        doc.set("excludedOrder", JSON.valueToTree(outcome.excludedOrder()));
        doc.set("manuallyExcluded", JSON.valueToTree(outcome.manuallyExcluded()));
        doc.set("rounds", JSON.valueToTree(outcome.rounds()));
        return doc;
    }

    static List<Candidate> candidates(JsonNode doc) {
        List<Candidate> out = new ArrayList<>();
        doc.path("candidates").forEach(c -> out.add(new Candidate(c.path("id").asInt(), c.path("name").asText())));
        return out;
    }

    static List<List<Integer>> ballots(JsonNode doc) {
        List<List<Integer>> out = new ArrayList<>();
        doc.path("ballots").forEach(b -> out.add(ints(b)));
        return out;
    }

    static List<Integer> ints(JsonNode array) {
        List<Integer> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asInt()));
        return out;
    }

    static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static ObjectNode header(String contest, String region, String method, List<Candidate> candidates) {
        ObjectNode doc = JSON.createObjectNode();
        doc.put("contest", contest);
        doc.put("region", region);
        doc.put("method", method);
        ArrayNode list = doc.putArray("candidates");
        candidates.forEach(c -> list.addObject().put("id", c.id()).put("name", c.name()));
        return doc;
    }

    private static ObjectNode ballotBox(List<String> receipts) {
        ObjectNode box = JSON.createObjectNode();
        box.put("ballotCount", receipts.size());
        box.set("receipts", JSON.valueToTree(receipts.stream().sorted().toList()));
        box.put("receiptsMerkleRoot", MerkleRoot.of(receipts));
        return box;
    }

    private static List<List<Integer>> sorted(List<List<Integer>> ballots) {
        Comparator<List<Integer>> lexicographic = (a, b) -> {
            for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                int c = Integer.compare(a.get(i), b.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(a.size(), b.size());
        };
        return ballots.stream().sorted(lexicographic).toList();
    }
}