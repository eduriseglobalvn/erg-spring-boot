package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardEntryResponse;
import com.erg.ergspringboot.elearning.internal.engagement.ClassLeaderboardService.Board;
import com.erg.ergspringboot.elearning.internal.engagement.ClassLeaderboardService.Member;
import com.erg.ergspringboot.elearning.internal.engagement.ClassLeaderboardService.Tier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The board is ranked once at build time; per viewer only their own row changes. */
class ClassLeaderboardRankingTest {

    private static final List<Member> MEMBERS = List.of(
            new Member("s-b", "Bình", "u-b", false),
            new Member("s-a", "An", "u-a", false),
            new Member("s-c", "Cường", "u-c", true),
            new Member("s-d", "Dũng", "u-d", false));
    private static final Map<String, Tier> TIERS = Map.of("s-a", new Tier("silver", 2, "silver"), "s-c", new Tier("gold", 1, "gold"));

    private static List<ClassLeaderboardEntryResponse> ranked() {
        Map<String, Integer> xp = Map.of("s-a", 50, "s-b", 50, "s-c", 80);
        Map<String, Integer> previous = Map.of("s-a", 70, "s-b", 10);
        return ClassLeaderboardService.rank(MEMBERS, xp, previous, Map.of("s-a", 40), Map.of("s-a", 3), Map.of("s-a", 2), TIERS);
    }

    @Test
    void ranksByXpThenByNameAndLeavesUnscoredStudentsUnranked() {
        List<ClassLeaderboardEntryResponse> entries = ranked();

        assertThat(entries).extracting(ClassLeaderboardEntryResponse::studentId).containsExactly("s-c", "s-a", "s-b", "s-d");
        assertThat(entries).extracting(ClassLeaderboardEntryResponse::rank).containsExactly(1, 2, 3, null);
        // An and Bình tie on 50 XP: the name decides. An was rank 1 in the previous period (70 XP), now 2.
        assertThat(entries.get(1).rankChange()).isEqualTo(-1);
        assertThat(entries).noneMatch(ClassLeaderboardEntryResponse::isViewer);
    }

    @Test
    void aHiddenStudentShowsNoTierToOthersButKeepsItInTheirOwnRow() {
        List<ClassLeaderboardEntryResponse> entries = ranked();
        assertThat(entries.get(0).tierKey()).isNull(); // Cường hid themselves
        Map<String, Integer> index = new HashMap<>();
        index.put("u-c", 0);
        Board board = new Board(null, null, null, null, Set.of("s-c"), entries, index, TIERS);

        List<ClassLeaderboardEntryResponse> forCuong = ClassLeaderboardService.forViewer(board, "u-c");

        assertThat(forCuong.get(0).isViewer()).isTrue();
        assertThat(forCuong.get(0).tierKey()).isEqualTo("gold");
        assertThat(forCuong.get(0).rank()).isEqualTo(1);
        assertThat(forCuong.subList(1, 4)).noneMatch(ClassLeaderboardEntryResponse::isViewer);
        // The shared ranked list is not touched, and a login that is not on the board gets it as is.
        assertThat(entries.get(0).isViewer()).isFalse();
        assertThat(ClassLeaderboardService.forViewer(board, "u-nobody")).isSameAs(entries);
    }
}
