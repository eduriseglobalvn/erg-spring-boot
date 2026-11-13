package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardEntryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.LeaderboardMeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassLeaderboardMeTest {

    private static ClassLeaderboardEntryResponse entry(Integer rank, String id, int xp, boolean viewer) {
        return new ClassLeaderboardEntryResponse(rank, id, id, xp, 0, null, viewer, null, 0, 0, null, null, null, false);
    }

    @Test
    void meReportsRankSizeAndXpToPassTheStudentAbove() {
        List<ClassLeaderboardEntryResponse> cls = List.of(entry(1, "a", 100, false), entry(2, "b", 60, false), entry(3, "me", 20, true), entry(null, "z", 0, false));
        List<ClassLeaderboardEntryResponse> grade = List.of(entry(1, "a", 100, false), entry(2, "q", 70, false), entry(3, "b", 60, false),
                entry(4, "me", 20, true), entry(null, "z", 0, false));
        LeaderboardMeResponse me = ClassLeaderboardService.me(cls, grade);
        assertThat(me.rank()).isEqualTo(3);
        assertThat(me.size()).isEqualTo(4);
        assertThat(me.gradeRank()).isEqualTo(4);
        assertThat(me.gradeSize()).isEqualTo(5);
        assertThat(me.xpToNextRank()).isEqualTo(41);
        assertThat(me.nextRankName()).isEqualTo("Hạng 2");
    }

    @Test
    void firstPlaceAndUnrankedHaveNoNextRank() {
        List<ClassLeaderboardEntryResponse> first = List.of(entry(1, "me", 50, true), entry(2, "b", 10, false));
        assertThat(ClassLeaderboardService.me(first, first).xpToNextRank()).isNull();
        List<ClassLeaderboardEntryResponse> none = List.of(entry(1, "b", 10, false), entry(null, "me", 0, true));
        LeaderboardMeResponse me = ClassLeaderboardService.me(none, none);
        assertThat(me.rank()).isNull();
        assertThat(me.xpToNextRank()).isNull();
    }
}
