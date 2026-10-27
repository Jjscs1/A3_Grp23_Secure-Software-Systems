package com.evp.tally;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TallyVerifier {

    private TallyVerifier() {
    }

    public static List<String> problems(JsonNode published) {
        List<String> problems = new ArrayList<>();
        String where = published.path("contest").asText() + " " + published.path("region").asText();
        JsonNode inputs = published.path("countInputs");
        JsonNode box = published.path("ballotBox");

        List<String> receipts = TallyDocument.strings(box.path("receipts"));
        if (!MerkleRoot.of(receipts).equals(box.path("receiptsMerkleRoot").asText())) {
            problems.add(where + ": receipt list does not match the published Merkle root");
        }
        if (receipts.size() != box.path("ballotCount").asInt()) {
            problems.add(where + ": ballot count does not match the number of receipts");
        }

        List<List<Integer>> ballots = TallyDocument.ballots(published);
        if (ballots.size() != published.path("formal").asInt()) {
            problems.add(where + ": " + ballots.size() + " ballots published, formal = "
                    + published.path("formal").asInt());
        }

        List<Integer> excluded = TallyDocument.ints(inputs.path("excluded"));
        Set<Integer> ordered = new HashSet<>();
        published.path("recountOrders").forEach(o -> ordered.add(o.path("candidateId").asInt()));
        if (!ordered.containsAll(excluded)) {
            problems.add(where + ": a candidate was excluded without an approved court order");
        }

        List<Integer> candidateIds = TallyDocument.ints(inputs.path("candidateIds"));
        long seed = inputs.path("tiebreakSeed").asLong();
        try {
            JsonNode again;
            if ("house".equals(published.path("contest").asText())) {
                again = TallyDocument.JSON.valueToTree(HouseCount.count(candidateIds, ballots, seed));
                if (!again.path("winner").equals(published.path("winner"))
                        || !again.path("tcp").equals(published.path("tcp"))) {
                    problems.add(where + ": recomputed winner / two-candidate result differs");
                }
            } else {
                again = TallyDocument.JSON.valueToTree(SenateCount.count(candidateIds, ballots,
                        inputs.path("vacancies").asInt(), excluded, seed));
                if (!again.path("elected").equals(published.path("elected"))
                        || !again.path("quota").equals(published.path("quota"))) {
                    problems.add(where + ": recomputed senators elected differ");
                }
            }
            if (!again.path("rounds").equals(published.path("rounds"))) {
                problems.add(where + ": recomputed distribution of preferences differs");
            }
        } catch (RuntimeException e) {
            problems.add(where + ": the published data cannot be counted (" + e.getMessage() + ")");
        }
        return problems;
    }
}