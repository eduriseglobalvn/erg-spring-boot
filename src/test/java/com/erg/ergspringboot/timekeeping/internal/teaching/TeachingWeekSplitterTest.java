package com.erg.ergspringboot.timekeeping.internal.teaching;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.timekeeping.TeachingWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeachingWeekSplitterTest {

    private static List<String> labels(String month) {
        return TeachingWeekSplitter.split(YearMonth.parse(month)).stream().map(TeachingWeek::label).toList();
    }

    @Test
    void t9Of2026StartsOnATuesday() {
        assertThat(labels("2026-09")).containsExactly("01-04", "07-11", "14-18", "21-25", "28-30");
    }

    @Test
    void t10Of2026LastWeekRunsToTheSaturdayEndOfMonth() {
        assertThat(labels("2026-10")).containsExactly("01-02", "05-09", "12-16", "19-23", "26-31");
    }

    @Test
    void t11Of2026StartsOnASundayWhichJoinsTheNextWeek() {
        List<TeachingWeek> weeks = TeachingWeekSplitter.split(YearMonth.parse("2026-11"));
        assertThat(weeks).extracting(TeachingWeek::label).containsExactly("02-06", "09-13", "16-20", "23-27", "30");
        assertThat(weeks.get(0).from()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void aMonthStartingOnASaturdayMergesSaturdayAndSundayIntoTheSecondWeek() {
        List<TeachingWeek> weeks = TeachingWeekSplitter.split(YearMonth.parse("2026-08"));
        assertThat(weeks).extracting(TeachingWeek::label).containsExactly("03-07", "10-14", "17-21", "24-28", "31");
        assertThat(weeks.get(0).from()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void februaryHasFourColumnsAndTheLastEndsOnTheSaturday() {
        assertThat(labels("2026-02")).containsExactly("02-06", "09-13", "16-20", "23-28");
    }

    @Test
    void everyMonthOfTwoYearsIsContiguousAndHasAtMostFiveColumns() {
        for (YearMonth m = YearMonth.of(2025, 1); m.isBefore(YearMonth.of(2027, 1)); m = m.plusMonths(1)) {
            List<TeachingWeek> weeks = TeachingWeekSplitter.split(m);
            assertThat(weeks).hasSizeLessThanOrEqualTo(5);
            assertThat(weeks.get(0).from()).isEqualTo(m.atDay(1));
            assertThat(weeks.get(weeks.size() - 1).to()).isEqualTo(m.atEndOfMonth());
            for (int i = 1; i < weeks.size(); i++) {
                assertThat(weeks.get(i).from()).isEqualTo(weeks.get(i - 1).to().plusDays(1));
            }
        }
    }
}
