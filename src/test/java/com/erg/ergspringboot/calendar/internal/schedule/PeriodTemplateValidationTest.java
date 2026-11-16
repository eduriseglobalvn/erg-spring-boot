package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput.Slot;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rules of a period table: contiguous numbers, ordered sessions and times, no overlap, at most 10 timed periods. */
class PeriodTemplateValidationTest {

    private static Slot slot(int no, String session, String start, String end) {
        return new Slot(no, session, start, end);
    }

    private static void rejects(List<Slot> slots, String code) {
        assertThatThrownBy(() -> PeriodTemplateAdminService.validSlots(slots)).isInstanceOfSatisfying(
                TeachingScheduleException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void acceptsAnOrderedTableWithAnEveningAndSwitchedOffPeriods() {
        List<Slot> slots = List.of(slot(2, "MORNING", "08:00", "08:45"), slot(1, "MORNING", "07:15", "08:00"),
                slot(3, "MORNING", null, null), slot(4, "AFTERNOON", "13:30", "14:15"), slot(5, "EVENING", "18:00", "18:45"));

        assertThat(PeriodTemplateAdminService.validSlots(slots)).extracting(Slot::periodNo).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void rejectsGapsBadSessionsAndBadTimes() {
        rejects(List.of(slot(1, "MORNING", "07:00", "07:45"), slot(3, "MORNING", "08:00", "08:45")), "PERIOD_NUMBERS_INVALID");
        rejects(List.of(slot(1, "NIGHT", "07:00", "07:45")), "SESSION_INVALID");
        rejects(List.of(slot(1, "AFTERNOON", "13:00", "13:45"), slot(2, "MORNING", "14:00", "14:45")), "SESSION_ORDER_INVALID");
        rejects(List.of(slot(1, "MORNING", "07:00", null)), "PERIOD_TIME_INCOMPLETE");
        rejects(List.of(slot(1, "MORNING", "7:00", "07:45")), "PERIOD_TIME_INVALID");
        rejects(List.of(slot(1, "MORNING", "08:00", "07:45")), "PERIOD_TIME_ORDER");
        rejects(List.of(slot(1, "MORNING", "07:00", "07:45"), slot(2, "MORNING", "07:40", "08:25")), "PERIOD_TIME_OVERLAP");
        rejects(List.of(slot(1, "MORNING", null, null)), "NO_TIMED_PERIOD");
        rejects(List.of(), "PERIODS_COUNT_INVALID");
    }

    @Test
    void rejectsMoreThanTenTimedPeriodsButAllowsTwelveRowsWhenTwoAreOff() {
        List<Slot> eleven = new ArrayList<>();
        for (int i = 1; i <= 11; i++) eleven.add(slot(i, "MORNING", String.format("%02d:00", 5 + i), String.format("%02d:45", 5 + i)));
        rejects(eleven, "TOO_MANY_TIMED_PERIODS");

        List<Slot> twelve = new ArrayList<>(eleven.subList(0, 10));
        twelve.add(slot(11, "EVENING", null, null));
        twelve.add(slot(12, "EVENING", null, null));
        assertThat(PeriodTemplateAdminService.validSlots(twelve)).hasSize(12);
    }
}
