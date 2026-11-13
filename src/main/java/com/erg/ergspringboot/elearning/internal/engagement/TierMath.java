package com.erg.ergspringboot.elearning.internal.engagement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure tier arithmetic of the student gamification spec (5.3-5.6): 8 tiers by percentile bands of the grade,
 * 5 divisions (5 lowest, 1 highest) in every tier but Cao thu, at least {@link #MIN_SUBMITTED} hand-ins to be ranked,
 * and a drop only after two consecutive updates below the cutoff.
 */
final class TierMath {

    static final int MIN_SUBMITTED = 3;
    static final int DIVISIONS = 5;
    static final int DROP_AFTER_UPDATES = 2;

    /** {@code cumulativePercent}: the band ends at this share of the grade, counted from the top. */
    record TierDef(String key, String share, int cumulativePercent) {
        boolean hasDivisions() {
            return !"ct".equals(key);
        }
    }

    /** Highest first. */
    static final List<TierDef> TIERS = List.of(
            new TierDef("ct", "Top 3%", 3), new TierDef("kc", "3–12%", 12), new TierDef("bk", "12–25%", 25),
            new TierDef("vang", "25–45%", 45), new TierDef("bac", "45–65%", 65), new TierDef("dong", "65–82%", 82),
            new TierDef("sat", "82–94%", 94), new TierDef("go", "6% cuối", 100));

    record Candidate(String studentId, int xp, int submitted, Instant reachedAt) { }

    record Placement(String studentId, int rank, int xp, String tierKey, Integer division) { }

    /** A band of ranks; {@code division} is null for Cao thu. {@code minXp}: XP of the last student inside. */
    record Cell(String tierKey, Integer division, int rankFrom, int rankTo, int minXp) { }

    record Standing(List<Placement> placements, List<Cell> cells, int size) { }

    record State(String tierKey, Integer division, int belowCount, boolean protect) { }

    private TierMath() {
    }

    static TierDef tier(String key) {
        return TIERS.stream().filter(def -> def.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier " + key));
    }

    /** Seats per tier (same order as {@link #TIERS}): rounded cumulative bands, the remainder goes to the last tier. */
    static int[] seats(int n) {
        int[] seats = new int[TIERS.size()];
        int previousCut = 0;
        for (int i = 0; i < TIERS.size(); i++) {
            int cut = i == TIERS.size() - 1 ? n : (TIERS.get(i).cumulativePercent() * n + 50) / 100;
            if (i == 0 && n >= 10) cut = Math.max(1, cut);
            seats[i] = cut - previousCut;
            previousCut = cut;
        }
        return seats;
    }

    /** Ranks every candidate with at least {@link #MIN_SUBMITTED} hand-ins; earlier to reach the same XP ranks higher. */
    static Standing standings(List<Candidate> candidates) {
        List<Candidate> ranked = candidates.stream().filter(c -> c.submitted() >= MIN_SUBMITTED)
                .sorted(Comparator.comparingInt(Candidate::xp).reversed()
                        .thenComparing(Candidate::reachedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Candidate::studentId)).toList();
        int[] seats = seats(ranked.size());
        List<Placement> placements = new ArrayList<>(ranked.size());
        List<Cell> cells = new ArrayList<>();
        int next = 0;
        for (int t = 0; t < TIERS.size(); t++) {
            TierDef def = TIERS.get(t);
            int divisions = def.hasDivisions() ? DIVISIONS : 1;
            for (int d = 1; d <= divisions; d++) {
                int size = def.hasDivisions() ? seats[t] / DIVISIONS + (d <= seats[t] % DIVISIONS ? 1 : 0) : seats[t];
                if (size == 0) continue;
                Integer division = def.hasDivisions() ? d : null;
                for (int i = next; i < next + size; i++) {
                    Candidate c = ranked.get(i);
                    placements.add(new Placement(c.studentId(), i + 1, c.xp(), def.key(), division));
                }
                cells.add(new Cell(def.key(), division, next + 1, next + size, ranked.get(next + size - 1).xp()));
                next += size;
            }
        }
        return new Standing(placements, cells, ranked.size());
    }

    /** 0 = lowest (go 5) ... 35 = Cao thu. */
    static int position(String tierKey, Integer division) {
        int fromBottom = TIERS.size() - 1 - TIERS.indexOf(tier(tierKey));
        return tier(tierKey).hasDivisions() ? fromBottom * DIVISIONS + (DIVISIONS - division) : fromBottom * DIVISIONS;
    }

    /**
     * Moves up at once, moves down only when below the cutoff on {@link #DROP_AFTER_UPDATES} updates in a row (the
     * first time it only sets {@code protect}). {@code computed} is null when the student is not ranked.
     */
    static State next(State previous, Placement computed) {
        if (computed == null) return previous == null ? new State(null, null, 0, false) : previous;
        State target = new State(computed.tierKey(), computed.division(), 0, false);
        if (previous == null || previous.tierKey() == null
                || position(computed.tierKey(), computed.division()) >= position(previous.tierKey(), previous.division())) {
            return target;
        }
        int below = previous.belowCount() + 1;
        return below >= DROP_AFTER_UPDATES ? target : new State(previous.tierKey(), previous.division(), below, true);
    }
}
