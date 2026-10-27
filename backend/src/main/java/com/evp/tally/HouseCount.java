package com.evp.tally;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class HouseCount {

    private HouseCount() {
    }
        
    public static HouseResult count(List<Integer> candidateIds, List<List<Integer>> ballots, long seed) {
        if (candidateIds.isEmpty()) {
            throw new IllegalArgumentException("No candidates");
        }
        Random lot = new Random(seed);
        List<Integer> continuing = new ArrayList<>(candidateIds);
        List<Map<String, Object>> rounds = new ArrayList<>();
        Integer majorityRound = null;

        if (continuing.size() == 1) { 
            int only = continuing.get(0);
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", 1);
            round.put("tallies", Map.of(String.valueOf(only), ballots.size()));
            round.put("exhausted", 0);
            round.put("note", "elected unopposed");
            rounds.add(round);
            return new HouseResult(only, ballots.size(), rounds, Map.of(String.valueOf(only), ballots.size()), 1);
        }

        List<Map<Integer, Fraction>> history = new ArrayList<>();
        while (true) {
            Set<Integer> stillIn = new HashSet<>(continuing);
            Map<Integer, Integer> votes = new LinkedHashMap<>();
            continuing.forEach(cid -> votes.put(cid, 0));
            int exhausted = 0;
            for (List<Integer> preferences : ballots) {
                Integer receiver = null;
                for (int cid : preferences) {
                    if (stillIn.contains(cid)) {
                        receiver = cid;
                        break;
                    }
                }
                if (receiver == null) {
                    exhausted++;
                } else {
                    votes.merge(receiver, 1, Integer::sum);
                }
            }
            Map<Integer, Fraction> snapshot = new LinkedHashMap<>();
            votes.forEach((cid, v) -> snapshot.put(cid, Fraction.of(v)));
            history.add(snapshot);

            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", history.size());
            round.put("tallies", stringKeys(votes));
            round.put("exhausted", exhausted);

            int live = ballots.size() - exhausted;
            int top = votes.values().stream().max(Integer::compare).orElse(0);
            if (majorityRound == null && live > 0 && top * 2 > live) {
                majorityRound = history.size();
            }

            if (continuing.size() == 2) {
                int first = continuing.get(0);
                int second = continuing.get(1);
                int winner;
                String rule;
                if (votes.get(first).equals(votes.get(second))) {
                    winner = lot.nextBoolean() ? first : second;
                    rule = "lot (tied two-candidate-preferred)";
                } else {
                    winner = votes.get(first) > votes.get(second) ? first : second;
                    rule = "majority";
                }
                round.put("elected", winner);
                round.put("rule", rule);
                rounds.add(round);
                return new HouseResult(winner, ballots.size(), rounds, stringKeys(votes), majorityRound);
            }

            int fewest = votes.values().stream().min(Integer::compare).orElseThrow();
            List<Integer> lowest = continuing.stream().filter(cid -> votes.get(cid) == fewest).toList();
            TieBreak.Choice choice = lowest.size() == 1
                    ? new TieBreak.Choice(lowest.get(0), "lowest votes")
                    : TieBreak.choose(lowest, history, lot);
            round.put("excluded", choice.candidate());
            round.put("rule", choice.rule());
            rounds.add(round);
            continuing.remove(Integer.valueOf(choice.candidate()));
        }
    }

    private static Map<String, Integer> stringKeys(Map<Integer, Integer> votes) {
        Map<String, Integer> out = new LinkedHashMap<>();
        votes.forEach((cid, v) -> out.put(String.valueOf(cid), v));
        return out;
    }

    public record HouseResult(int winner, int formal, List<Map<String, Object>> rounds,
                              Map<String, Integer> tcp, Integer majorityRound) {
    }
}