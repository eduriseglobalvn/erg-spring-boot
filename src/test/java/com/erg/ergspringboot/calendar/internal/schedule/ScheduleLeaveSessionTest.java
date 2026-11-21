package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Leave;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AM/PM leave follows the start time against noon, the rule timekeeping uses, not the period number. */
class ScheduleLeaveSessionTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);

    private static Occurrence at(int periodNo, String start) {
        OffsetDateTime from = OffsetDateTime.parse("2026-11-18T" + start + ":00+07:00");
        return new Occurrence("e", "e", null, "b", "b", "PUBLISHED", "s", "s", "c", "c", null, null, "m", List.of(), DAY, 3, periodNo,
                from, from.plusMinutes(45), null, null, null, true);
    }

    @Test
    void sessionIsDecidedByStartTimeNotByPeriodNumber() {
        Leave morning = new Leave("u", DAY, DAY, "AM");
        Leave afternoon = new Leave("u", DAY, DAY, "PM");

        // a school whose period 5 is an afternoon lesson and whose period 7 starts at 08:00
        assertThat(morning.covers(at(5, "13:00"))).isFalse();
        assertThat(afternoon.covers(at(5, "13:00"))).isTrue();
        assertThat(morning.covers(at(7, "08:00"))).isTrue();
        assertThat(afternoon.covers(at(7, "08:00"))).isFalse();
        assertThat(new Leave("u", DAY, DAY, "FULL").covers(at(7, "08:00"))).isTrue();
    }
}
