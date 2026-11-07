package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.DayPart;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AffectedPeriodsServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private final CalendarReadPort calendar = mock(CalendarReadPort.class);
    private final AffectedPeriodsService service = new AffectedPeriodsService(calendar);

    // 08:00 and 13:30 Asia/Ho_Chi_Minh
    private final TeacherPeriod morning = period("2026-11-12T01:00:00Z", "7A");
    private final TeacherPeriod afternoon = period("2026-11-12T06:30:00Z", "8B");

    @Test
    void fullDayReturnsEveryPeriodInTheRangeAndHalfDaysSplitAtNoon() {
        when(calendar.teacherPeriods("t", "u", DAY, DAY)).thenReturn(List.of(morning, afternoon));

        assertThat(service.affected("t", "u", DAY, DAY, DayPart.FULL)).containsExactly(morning, afternoon);
        assertThat(service.affected("t", "u", DAY, DAY, DayPart.AM)).containsExactly(morning);
        assertThat(service.affected("t", "u", DAY, DAY, DayPart.PM)).containsExactly(afternoon);
    }

    private static TeacherPeriod period(String start, String className) {
        Instant s = Instant.parse(start);
        return new TeacherPeriod(s, s.plusSeconds(2700), className, "Toán", "P201");
    }
}
