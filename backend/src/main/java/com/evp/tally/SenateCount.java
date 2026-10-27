package com.evp.tally;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class SenateCount {

    private enum Status { CONTINUING, ELECTED, EXCLUDED }

    private final int vacancies;
    private final Random lot;
    private final List<Integer> candidates;        
    private final Map<Integer, Status> status = new HashMap<>();
    private final List<List<Integer>> ballots;       
    private final Fraction[] weight;                 
    private final int[] position;                    
   private final Map<Integer, List<Integer>> piles = new HashMap<>(); 
    private Fraction exhausted = Fraction.ZERO;

    private final int formal;
    private final int quota;
    private final List<Integer> elected = new ArrayList<>();
    private final List<Integer> excludedOrder = new ArrayList<>();
    private final List<Integer> manuallyExcluded = new ArrayList<>();
    private final List<Map<String, Object>> rounds = new ArrayList<>();
    private final List<Map<Integer, Fraction>> history = new ArrayList<>();
    private final Deque<Integer> surplusesToTransfer = new ArrayDeque<>();

    private SenateCount(List<Integer> candidateIds, List<List<Integer>> allBallots, int vacancies,
                        List<Integer> courtExcluded, long seed) {
        this.vacancies = vacancies;
        this.lot = new Random(seed);
        Set<Integer> out = new HashSet<>(courtExcluded);
        this.candidates = candidateIds.stream().filter(cid -> !out.contains(cid)).toList();
        candidateIds.stream().filter(out::contains).forEach(manuallyExcluded::add);
        candidates.forEach(cid -> {
            status.put(cid, Status.CONTINUING);
            piles.put(cid, new ArrayList<>());
        });

        Set<Integer> inCount = new HashSet<>(candidates);
        this.ballots = allBallots.stream().map(p -> p.stream().filter(inCount::contains).toList()).toList();
        this.weight = new Fraction[ballots.size()];
        java.util.Arrays.fill(weight, Fraction.ONE);
        this.position = new int[ballots.size()];
        this.formal = allBallots.size();
        this.quota = formal / (vacancies + 1) + 1;
    }

    public static SenateResult count(List<Integer> candidateIds, List<List<Integer>> ballots, int vacancies,
                                     List<Integer> courtExcluded, long seed) {
        if (vacancies < 1) {
            throw new IllegalArgumentException("At least one vacancy is required");
        }
        return new SenateCount(candidateIds, ballots, vacancies, courtExcluded, seed).run();
    }

    static Fraction transferValue(Fraction surplus, Fraction total) {
        return surplus.dividedBy(total);
    }


    private SenateResult run() {
        List<Integer> all = new ArrayList<>();
        for (int b = 0; b < ballots.size(); b++) {
            all.add(b);
        }
        giveBallots(all, Fraction.ONE);
        record("first preferences", Map.of());

        while (!candidates.isEmpty() && seatsLeft() > 0) {
            Map<Integer, Fraction> votes = new LinkedHashMap<>();
            continuing().forEach(cid -> votes.put(cid, votes(cid)));
            Comparator<Integer> highestFirst = Comparator
                    .comparing((Integer cid) -> votes.getOrDefault(cid, Fraction.ZERO)).reversed()
                    .thenComparingInt(candidates::indexOf);

            Fraction quotaValue = Fraction.of(quota);
            List<Integer> reachedQuota = votes.keySet().stream()
                    .filter(cid -> votes.get(cid).compareTo(quotaValue) >= 0)
                    .sorted(highestFirst).toList();
            List<Integer> newlyElected = new ArrayList<>();
            for (int cid : reachedQuota) {
                if (seatsLeft() > 0) {
                    elect(cid);
                    newlyElected.add(cid);
                }
            }
            noteElected(newlyElected);
            if (seatsLeft() == 0) {
                break;
            }

            List<Integer> remaining = new ArrayList<>(continuing());
            if (remaining.size() <= seatsLeft()) {
                remaining.sort(highestFirst);
                remaining.forEach(this::elect);
                noteElected(remaining);
                rounds.get(rounds.size() - 1).put("note", "remaining candidates equal remaining vacancies");
                break;
            }

            if (!surplusesToTransfer.isEmpty()) {
                transferSurplus(surplusesToTransfer.pollFirst());
                continue;
            }

            excludeLowest(votes);
        }
        return new SenateResult(vacancies, formal, quota, List.copyOf(elected), List.copyOf(excludedOrder),
                List.copyOf(manuallyExcluded), rounds);
    }

    private void transferSurplus(int cid) {
        Fraction total = votes(cid);
        Fraction surplus = total.minus(Fraction.of(quota));
        if (surplus.signum() <= 0) {
            return;
        }
        Fraction value = transferValue(surplus, total);
        List<Integer> pile = piles.put(cid, new ArrayList<>());
        giveBallots(pile, value);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("candidate", cid);
        details.put("surplus", surplus.display());
        details.put("transferValue", value.display());
        record("surplus transfer", details);
    }

    private void excludeLowest(Map<Integer, Fraction> votes) {
        Fraction fewest = votes.values().stream().min(Fraction::compareTo).orElseThrow();
        List<Integer> lowest = continuing().stream().filter(cid -> votes.get(cid).compareTo(fewest) == 0).toList();
        TieBreak.Choice choice = lowest.size() == 1
                ? new TieBreak.Choice(lowest.get(0), "lowest votes")
                : TieBreak.choose(lowest, history, lot);

        int out = choice.candidate();
        status.put(out, Status.EXCLUDED);
        excludedOrder.add(out);
        List<Integer> pile = piles.put(out, new ArrayList<>());
        giveBallots(pile, Fraction.ONE); // passed on at their current value
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("candidate", out);
        details.put("rule", choice.rule());
        record("exclusion", details);
    }


    private List<Integer> continuing() {
        return candidates.stream().filter(cid -> status.get(cid) == Status.CONTINUING).toList();
    }

    private Fraction votes(int cid) {
        Fraction total = Fraction.ZERO;
        for (int b : piles.get(cid)) {
            total = total.plus(weight[b]);
        }
        return total;
    }

    private int seatsLeft() {
        return vacancies - elected.size();
    }

    private Integer nextPreference(int b) {
        List<Integer> prefs = ballots.get(b);
        int pos = position[b];
        while (pos < prefs.size() && status.get(prefs.get(pos)) != Status.CONTINUING) {
            pos++;
        }
        position[b] = pos;
        return pos < prefs.size() ? prefs.get(pos) : null;
    }

    private void giveBallots(List<Integer> ballotNumbers, Fraction multiplyWeightBy) {
        for (int b : ballotNumbers) {
            weight[b] = weight[b].times(multiplyWeightBy);
            Integer receiver = nextPreference(b);
            if (receiver == null) {
                exhausted = exhausted.plus(weight[b]);
            } else {
                piles.get(receiver).add(b);
            }
        }
    }

    private void elect(int cid) {
        status.put(cid, Status.ELECTED);
        elected.add(cid);
        surplusesToTransfer.addLast(cid);
    }

    @SuppressWarnings("unchecked")
    private void noteElected(List<Integer> cids) {
        if (!cids.isEmpty()) {
            Map<String, Object> last = rounds.get(rounds.size() - 1);
            ((List<Integer>) last.computeIfAbsent("elected", k -> new ArrayList<Integer>())).addAll(cids);
        }
    }

    private void record(String action, Map<String, Object> details) {
        Map<Integer, Fraction> tallies = new LinkedHashMap<>();
        continuing().forEach(cid -> tallies.put(cid, votes(cid)));
        history.add(tallies);

        Map<String, Double> shown = new LinkedHashMap<>();
        tallies.forEach((cid, v) -> shown.put(String.valueOf(cid), v.display()));
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("count", rounds.size() + 1);
        round.put("action", action);
        round.put("tallies", shown);
        round.put("exhausted", exhausted.display());
        round.putAll(details);
        rounds.add(round);
    }

    public record SenateResult(int vacancies, int formal, int quota, List<Integer> elected,
                               List<Integer> excludedOrder, List<Integer> manuallyExcluded,
                               List<Map<String, Object>> rounds) {
    }
}