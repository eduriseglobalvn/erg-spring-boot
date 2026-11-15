package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.internal.schedule.TeachingScheduleAccessQuery.Held;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** The 2-week window of KE_HOACH_LICH_DAY.md 2.4, pure date math in Vietnam time. */
class TeachingScheduleAccessWindowTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static OffsetDateTime vn(String date, String time) {
        return LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(VN).toOffsetDateTime();
    }

    /** Access holds while the last event ends on or after the cut-off. */
    private static boolean holds(OffsetDateTime lastEnd, OffsetDateTime now) {
        return !lastEnd.isBefore(TeachingScheduleAccessQuery.since(now));
    }

    @Test
    void lastPeriodOnFridayThe13thKeepsAccessThrough29thAt2359AndLosesItOn30thAtMidnight() {
        OffsetDateTime lastEnd = vn("2026-11-13", "17:00");

        assertThat(holds(lastEnd, vn("2026-11-29", "23:59"))).isTrue();
        assertThat(holds(lastEnd, vn("2026-11-30", "00:00"))).isFalse();
        assertThat(new Held("t", "s", "c", "9A2", lastEnd).revokeOn()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void lastPeriodOnMondayThe9thStillKeepsTheWholeWeekAndTheTwoAfterIt() {
        OffsetDateTime lastEnd = vn("2026-11-09", "08:45");

        assertThat(holds(lastEnd, vn("2026-11-29", "23:59"))).isTrue();
        assertThat(holds(lastEnd, vn("2026-11-30", "00:00"))).isFalse();
        assertThat(new Held("t", "s", "c", "9A2", lastEnd).revokeOn()).isEqualTo(LocalDate.of(2026, 11, 30));
    }
}
