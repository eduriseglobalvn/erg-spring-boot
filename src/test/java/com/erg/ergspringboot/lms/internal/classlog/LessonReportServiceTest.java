package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.internal.classlog.LessonReportService.Taught;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LessonReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private static Taught t(LocalDate date, boolean confirmed, boolean late) {
        return new Taught("u", "s", date, confirmed, late);
    }

    @Test
    void anUnconfirmedPastDayIsMissingButTodayIsStillOpen() {
        LocalDate past = TODAY.minusDays(1);
        assertThat(LessonReportService.state(List.of(t(past, false, false)), past, TODAY)).isEqualTo("MISSING");
        assertThat(LessonReportService.state(List.of(t(TODAY, false, false)), TODAY, TODAY)).isEqualTo("NONE");
        assertThat(LessonReportService.state(List.of(), past, TODAY)).isEqualTo("NONE");
    }

    @Test
    void aConfirmedDayIsLateWhenAnyPeriodWasConfirmedLate() {
        LocalDate day = TODAY.minusDays(1);
        assertThat(LessonReportService.state(List.of(t(day, true, false), t(day, true, true)), day, TODAY)).isEqualTo("LATE");
        assertThat(LessonReportService.state(List.of(t(day, true, false)), day, TODAY)).isEqualTo("ON_TIME");
        // one missing period outweighs the confirmed ones
        assertThat(LessonReportService.state(List.of(t(day, true, true), t(day, false, false)), day, TODAY)).isEqualTo("MISSING");
    }
}
