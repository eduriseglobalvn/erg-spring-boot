package com.erg.ergspringboot.timekeeping.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.timekeeping.DayPart;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeaveDayCounterTest {

    private static final Set<DayOfWeek> MON_FRI = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final LocalDate THU = LocalDate.of(2026, 11, 12);
    private static final LocalDate FRI = LocalDate.of(2026, 11, 13);
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Test
    void twoWorkdays() {
        assertThat(LeaveDayCounter.count(THU, FRI, DayPart.FULL, MON_FRI, Set.of())).isEqualByComparingTo("2");
    }

    @Test
    void weekendIsSkipped() {
        assertThat(LeaveDayCounter.count(FRI, MON, DayPart.FULL, MON_FRI, Set.of())).isEqualByComparingTo("2");
    }

    @Test
    void halfDay() {
        assertThat(LeaveDayCounter.count(THU, THU, DayPart.AM, MON_FRI, Set.of())).isEqualByComparingTo("0.5");
        assertThat(LeaveDayCounter.count(THU, THU, DayPart.PM, MON_FRI, Set.of())).isEqualByComparingTo("0.5");
    }

    @Test
    void holidayIsExcluded() {
        assertThat(LeaveDayCounter.count(THU, FRI, DayPart.FULL, MON_FRI, Set.of(FRI))).isEqualByComparingTo("1");
    }

    @Test
    void halfDayOnNonWorkdayOrHolidayIsZero() {
        LocalDate sat = LocalDate.of(2026, 11, 14);
        assertThat(LeaveDayCounter.count(sat, sat, DayPart.AM, MON_FRI, Set.of())).isEqualByComparingTo("0");
        assertThat(LeaveDayCounter.count(FRI, FRI, DayPart.PM, MON_FRI, Set.of(FRI))).isEqualByComparingTo("0");
    }

    @Test
    void fullSingleDay() {
        assertThat(LeaveDayCounter.count(THU, THU, DayPart.FULL, MON_FRI, Set.of())).isEqualByComparingTo("1");
    }

    @Test
    void invalidRanges() {
        assertThatThrownBy(() -> LeaveDayCounter.count(FRI, THU, DayPart.FULL, MON_FRI, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LeaveDayCounter.count(THU, FRI, DayPart.AM, MON_FRI, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
