package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SideResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleHolidayRestoreRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 2);

    private static SideResponse side(String eventId) {
        return new SideResponse(eventId, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static ConflictDetailResponse conflict(String severity, String mine, String other) {
        return new ConflictDetailResponse("c", "TEACHER_TIME", severity, null, 3, 1, null, null, null, 1, 1, side(mine),
                other == null ? null : side(other), null, null, null, null);
    }

    @Test
    void aFreeDayIsRestored() {
        List<ScheduleHolidayService.Cancelled> cancelled = List.of(new ScheduleHolidayService.Cancelled("e1", "s1", DAY));
        assertThat(ScheduleHolidayService.notCovered(cancelled, Map.of())).containsExactly("e1");
    }

    @Test
    void aDayAnotherCancelHolidayStillCoversStaysCancelled() {
        List<ScheduleHolidayService.Cancelled> cancelled = List.of(new ScheduleHolidayService.Cancelled("e1", "s1", DAY),
                new ScheduleHolidayService.Cancelled("e2", "s2", DAY), new ScheduleHolidayService.Cancelled("e3", "s3", DAY));
        assertThat(ScheduleHolidayService.notCovered(cancelled, Map.of(DAY, Set.of("s1")))).containsExactly("e2", "e3");
        assertThat(ScheduleHolidayService.notCovered(cancelled, Map.of(DAY, Set.of("")))).isEmpty();
    }

    @Test
    void aPublishedPeriodNowInTheSlotBlocksButTwoPeriodsRestoredTogetherDoNot() {
        Set<String> restoring = Set.of("e1", "e3");
        List<ConflictDetailResponse> conflicts = List.of(conflict("ERROR", "e1", "taken"), conflict("ERROR", "e3", "e1"),
                conflict("WARNING", "e3", "taken"));
        assertThat(ScheduleHolidayService.blockedBy(conflicts, restoring)).containsExactly("e1");
    }
}
