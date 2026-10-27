package com.evp.tally;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class TieBreak {

    record Choice(int candidate, String rule) {
    }

    private TieBreak() {
    }

    static Choice choose(List<Integer> tied, List<Map<Integer, Fraction>> history, Random lot) {
        List<Integer> stillTied = new ArrayList<>(tied);
        for (int i = history.size() - 2; i >= 0; i--) {
            Map<Integer, Fraction> tallies = history.get(i);
            Fraction lowest = null;
            for (int cid : stillTied) {
                Fraction v = tallies.getOrDefault(cid, Fraction.ZERO);
                if (lowest == null || v.compareTo(lowest) < 0) {
                    lowest = v;
                }
            }
            List<Integer> lowestThen = new ArrayList<>();
            for (int cid : stillTied) {
                if (tallies.getOrDefault(cid, Fraction.ZERO).compareTo(lowest) == 0) {
                    lowestThen.add(cid);
                }
            }
            if (lowestThen.size() < stillTied.size()) {
                stillTied = lowestThen;
                if (stillTied.size() == 1) {
                    return new Choice(stillTied.get(0), "earlier count");
                }
            }
        }
        List<Integer> sorted = new ArrayList<>(stillTied);
        sorted.sort(null);
        return new Choice(sorted.get(lot.nextInt(sorted.size())), "lot");
    }
}