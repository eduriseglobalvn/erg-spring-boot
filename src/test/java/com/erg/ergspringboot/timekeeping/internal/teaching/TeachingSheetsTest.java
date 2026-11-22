package com.erg.ergspringboot.timekeeping.internal.teaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.TeachingPeriod;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.timekeeping.TeachingWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeachingSheetsTest {

    private static final List<TeachingWeek> OCT = TeachingWeekSplitter.split(YearMonth.of(2026, 10));

    private static TeachingPeriod period(String teacher, String role, String clazz, String date, int count) {
        Instant start = LocalDate.parse(date).atTime(8, 0).atZone(TeachingSheets.ZONE).toInstant();
        return new TeachingPeriod("e-" + teacher + clazz + date, teacher, "GV " + teacher, role, "c-" + clazz, clazz, "s1", "Tin",
                "sch1", "THCS A", "l1", "Level 1", start, start.plusSeconds(45L * 60 * count), 1, count);
    }

    @Test
    void aTwoPeriodEventCountsTwoAndLandsInItsWeekColumn() {
        List<TeachingSheets.Line> lines = TeachingSheets.lines(List.of(period("t1", "MAIN", "6A", "2026-10-06", 2),
                period("t1", "MAIN", "6B", "2026-10-13", 1), period("t1", "MAIN", "6A", "2026-10-14", 1)), OCT);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).weekTotals()).containsExactly(0, 2, 2, 0, 0);
        assertThat(lines.get(0).total()).isEqualTo(4);
        assertThat(lines.get(0).classes()).containsExactly("6A", "6B");
    }

    @Test
    void assistantAndMainAreSeparateSheets() {
        List<TeachingPeriod> events = List.of(period("t1", "MAIN", "6A", "2026-10-06", 1),
                period("t2", "ASSISTANT", "6A", "2026-10-06", 1));
        assertThat(TeachingSheets.ofRole(events, "MAIN")).extracting(TeachingPeriod::teacherUserId).containsExactly("t1");
        assertThat(TeachingSheets.ofRole(events, "ASSISTANT")).extracting(TeachingPeriod::teacherUserId).containsExactly("t2");
        assertThat(TeachingSheets.total(TeachingSheets.ofRole(events, "MAIN"), "t2")).isZero();
    }

    @Test
    void aMultiPeriodEventIsSpreadEvenlyOverItsPeriods() {
        List<TeachingSheets.Tiet> tiets = TeachingSheets.expand(List.of(period("t1", "MAIN", "6A", "2026-10-06", 2)));
        assertThat(tiets).extracting(TeachingSheets.Tiet::periodNo).containsExactly(1, 2);
        assertThat(tiets.get(0).to()).isEqualTo(tiets.get(1).from());
    }

    @Test
    void weekIndexIsMinusOneOutsideTheMonthAndRoleMustBeKnown() {
        assertThat(TeachingSheets.weekIndex(OCT, LocalDate.of(2026, 10, 31))).isEqualTo(4);
        assertThat(TeachingSheets.weekIndex(OCT, LocalDate.of(2026, 11, 2))).isEqualTo(-1);
        assertThat(TeachingSheets.role(null)).isEqualTo("MAIN");
        assertThatThrownBy(() -> TeachingSheets.role("BOSS")).isInstanceOf(ApiException.class);
    }

    @Test
    void anExportOrSheetOverTheCapIsRefusedBeforeItIsExpanded() {
        TeachingHoursService.checkExportSize(List.of(period("t1", "MAIN", "6A", "2026-10-06", 2)));
        List<TeachingPeriod> many = java.util.Collections.nCopies(TeachingHoursService.MAX_EXPORT_PERIODS + 1,
                period("t1", "MAIN", "6A", "2026-10-06", 1));
        assertThatThrownBy(() -> TeachingHoursService.checkExportSize(many)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "TEACHING_RANGE_TOO_LARGE");
    }
}
