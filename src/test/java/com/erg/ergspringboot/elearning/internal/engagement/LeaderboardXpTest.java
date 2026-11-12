package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp.Period;
import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp.Window;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeaderboardXpTest {

    private static ZonedDateTime vn(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, LeaderboardXp.ZONE);
    }

    @Test
    void weekRunsMondayToMondayInVietnamTime() {
        // Sunday 23:30 UTC is already Monday 06:30 in Vietnam.
        Window week = LeaderboardXp.window(Period.WEEK, ZonedDateTime.parse("2026-11-15T23:30:00Z"));
        assertThat(week.start()).isEqualTo(vn(2026, 11, 16, 0));
        assertThat(week.end()).isEqualTo(vn(2026, 11, 23, 0));
        assertThat(week.previous(Period.WEEK).start()).isEqualTo(vn(2026, 11, 9, 0));
    }

    @Test
    void monthAndTermsFollowTheSchoolYear() {
        assertThat(LeaderboardXp.window(Period.MONTH, vn(2026, 11, 12, 9)).start()).isEqualTo(vn(2026, 11, 1, 0));
        Window termOne = LeaderboardXp.window(Period.TERM, vn(2026, 11, 12, 9));
        assertThat(termOne.start()).isEqualTo(vn(2026, 9, 1, 0));
        assertThat(termOne.end()).isEqualTo(vn(2027, 1, 16, 0));
        Window termTwo = LeaderboardXp.window(Period.TERM, vn(2027, 3, 2, 9));
        assertThat(termTwo.start()).isEqualTo(vn(2027, 1, 16, 0));
        assertThat(termTwo.end()).isEqualTo(vn(2027, 9, 1, 0));
        assertThat(termTwo.previous(Period.TERM)).isEqualTo(termOne);
        assertThat(LeaderboardXp.window(Period.TERM, vn(2027, 1, 10, 9))).isEqualTo(termOne);
    }

    @Test
    void handInEarnsTenToFortyPlusTenBeforeTheDeadline() {
        assertThat(LeaderboardXp.handInXp(null, false)).isEqualTo(10);
        assertThat(LeaderboardXp.handInXp(10, false)).isEqualTo(25);
        assertThat(LeaderboardXp.handInXp(20, true)).isEqualTo(50);
        assertThat(LeaderboardXp.handInXp(60, false)).isEqualTo(40);
    }

    @Test
    void streakEndsTodayOrYesterdayAndSevenDaysGiveTheBonus() {
        LocalDate today = LocalDate.of(2026, 11, 12);
        Set<LocalDate> sevenUpToYesterday = Set.of(today.minusDays(1), today.minusDays(2), today.minusDays(3),
                today.minusDays(4), today.minusDays(5), today.minusDays(6), today.minusDays(7));
        assertThat(LeaderboardXp.streak(sevenUpToYesterday, today)).isEqualTo(7);
        assertThat(LeaderboardXp.streak(Set.of(today, today.minusDays(2)), today)).isEqualTo(1);
        assertThat(LeaderboardXp.streak(Set.of(today.minusDays(2)), today)).isZero();
        assertThat(LeaderboardXp.withStreakBonus(100, 7)).isEqualTo(120);
        assertThat(LeaderboardXp.withStreakBonus(100, 6)).isEqualTo(100);
    }
}
