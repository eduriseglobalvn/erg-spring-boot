package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Candidate;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Cell;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Placement;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.Standing;
import com.erg.ergspringboot.elearning.internal.engagement.TierMath.State;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TierMathTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    private static List<Candidate> grade(int n) {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Candidate("s%03d".formatted(i), 1000 - i * 3, 5, T0));
        return list;
    }

    @Test
    void seatsFollowTheRoundedCumulativeBandsAndSumToTheGrade() {
        assertThat(TierMath.seats(128)).containsExactly(4, 11, 17, 26, 25, 22, 15, 8);
        for (int n : new int[] {0, 1, 7, 10, 33, 128, 500}) {
            assertThat(java.util.Arrays.stream(TierMath.seats(n)).sum()).isEqualTo(n);
        }
        assertThat(TierMath.seats(10)[0]).isEqualTo(1); // Cao thu keeps a seat from 10 students
    }

    @Test
    void divisionsSplitSeatsEvenlyExtrasToTheHigherDivisions() {
        Standing standing = TierMath.standings(grade(128));
        List<Cell> dong = standing.cells().stream().filter(c -> c.tierKey().equals("dong")).toList();
        assertThat(dong).extracting(c -> c.rankTo() - c.rankFrom() + 1).containsExactly(5, 5, 4, 4, 4);
        assertThat(dong).extracting(Cell::division).containsExactly(1, 2, 3, 4, 5);
        assertThat(dong.getFirst().rankFrom()).isEqualTo(84); // 4+11+17+26+25 seats above
        Cell ct = standing.cells().getFirst();
        assertThat(ct.tierKey()).isEqualTo("ct");
        assertThat(ct.division()).isNull();
        assertThat(ct.minXp()).isEqualTo(1000 - 3 * 3); // XP of the last of the four
    }

    @Test
    void onlyStudentsWithThreeHandInsAreRankedAndTiesGoToTheEarlierOne() {
        List<Candidate> all = List.of(
                new Candidate("late", 50, 4, T0.plusSeconds(60)), new Candidate("early", 50, 4, T0),
                new Candidate("new", 999, 2, T0), new Candidate("low", 10, 3, T0));
        Standing standing = TierMath.standings(all);
        assertThat(standing.size()).isEqualTo(3);
        assertThat(standing.placements()).extracting(Placement::studentId).containsExactly("early", "late", "low");
    }

    @Test
    void movesUpAtOnceButDropsOnlyAfterTwoUpdatesBelowTheCutoff() {
        Placement silver = new Placement("s", 40, 100, "bac", 3);
        Placement bronze = new Placement("s", 90, 50, "dong", 1);
        State up = TierMath.next(new State("dong", 2, 0, false), silver);
        assertThat(up).isEqualTo(new State("bac", 3, 0, false));
        State first = TierMath.next(up, bronze);
        assertThat(first).isEqualTo(new State("bac", 3, 1, true));
        assertThat(TierMath.next(first, bronze)).isEqualTo(new State("dong", 1, 0, false));
        // Back above the cutoff in between resets the count.
        assertThat(TierMath.next(first, silver)).isEqualTo(new State("bac", 3, 0, false));
    }

    @Test
    void firstPlacementAndUnrankedStudents() {
        assertThat(TierMath.next(null, new Placement("s", 1, 9, "ct", null))).isEqualTo(new State("ct", null, 0, false));
        assertThat(TierMath.next(null, null).tierKey()).isNull();
        State kept = new State("kc", 2, 1, true);
        assertThat(TierMath.next(kept, null)).isEqualTo(kept);
        assertThat(TierMath.position("ct", null)).isGreaterThan(TierMath.position("kc", 1));
        assertThat(TierMath.position("kc", 5)).isGreaterThan(TierMath.position("bk", 1));
    }
}
