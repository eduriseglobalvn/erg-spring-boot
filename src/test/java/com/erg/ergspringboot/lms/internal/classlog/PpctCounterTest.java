package com.erg.ergspringboot.lms.internal.classlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.internal.classlog.PpctCounter.PeriodKey;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PpctCounterTest {

    private static LessonSlot slot(String event, String cls, String subject, String date, int start, int count) {
        return new LessonSlot(event, cls, subject, LocalDate.parse(date), start, count);
    }

    @Test
    void aTwoPeriodEventGivesNAndNPlusOneAndLaterDaysContinue() {
        Map<PeriodKey, Integer> ppct = PpctCounter.number(List.of(
                slot("e2", "6A6", "tin", "2026-11-12", 3, 1),
                slot("e1", "6A6", "tin", "2026-11-09", 1, 2)));

        assertThat(ppct).containsEntry(new PeriodKey("e1", 1), 1).containsEntry(new PeriodKey("e1", 2), 2)
                .containsEntry(new PeriodKey("e2", 3), 3);
    }

    @Test
    void classesAndSubjectsAreCountedSeparately() {
        Map<PeriodKey, Integer> ppct = PpctCounter.number(List.of(
                slot("a", "6A6", "tin", "2026-11-09", 1, 1),
                slot("b", "6A7", "tin", "2026-11-09", 2, 1),
                slot("c", "6A6", "toan", "2026-11-09", 3, 1)));

        assertThat(ppct.values()).containsOnly(1);
    }

    @Test
    void cancelledPeriodsAreNotCountedBecauseThePortOnlyReturnsPublishedSlots() {
        LessonScheduleReadPort port = mock(LessonScheduleReadPort.class);
        // The cancelled 10/11 event is absent from the port's answer: 12/11 is the 2nd period, not the 3rd.
        when(port.publishedSlots("t", List.of("6A6"), List.of("tin"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-13")))
                .thenReturn(List.of(slot("e1", "6A6", "tin", "2026-11-09", 1, 1), slot("e3", "6A6", "tin", "2026-11-12", 1, 1)));

        Map<PeriodKey, Integer> ppct = new PpctCounter(port).positions(
                "t", List.of("6A6"), List.of("tin"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-13"));

        assertThat(ppct).containsEntry(new PeriodKey("e3", 1), 2);
    }
}
