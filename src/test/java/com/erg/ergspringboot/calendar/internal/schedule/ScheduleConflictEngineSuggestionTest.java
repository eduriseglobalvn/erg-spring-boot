package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The fix search runs once per detect call (pools memoised), and gives the same answer as when it ran per conflict. */
class ScheduleConflictEngineSuggestionTest {

    private static final String TENANT = "t1";
    private static final String SCHOOL = "s1";
    private static final LocalDate MONDAY = LocalDate.of(2026, 12, 7);

    private final ScheduleDataLoader loader = mock(ScheduleDataLoader.class);
    private final UserDirectoryPort users = mock(UserDirectoryPort.class);
    private final ScheduleConflictEngine engine = new ScheduleConflictEngine(loader, mock(TravelTimeService.class), users);

    private static Occurrence occ(String eventId, String rowKey, String classId, String teacher, LocalDate date, int period,
            boolean mine) {
        OffsetDateTime start = date.atTime(LocalTime.of(6 + period, 0)).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
        return new Occurrence(eventId, rowKey, rowKey, "b", "Đợt 1", mine ? "DRAFT" : "PUBLISHED", SCHOOL, "School", classId,
                classId, null, null, "Toán", List.of(new TeacherRef(teacher, teacher, "MAIN")), date, date.getDayOfWeek().getValue(),
                period, start, start.plusMinutes(45), null, null, null, mine);
    }

    private static UserAccountProjection teacher(String id) {
        return new UserAccountProjection(id, TENANT, id, null, "ACTIVE");
    }

    @Test
    void twoConflictGroupsShareOnePoolLoadAndKeepTheOrderingRules() {
        // T1 is double-booked at periods 1 and 2; T2 is busy at period 1; T4 carries the heavier week
        Occurrence c1 = occ(null, "row1", "C1", "T1", MONDAY, 1, true);
        Occurrence c2 = occ(null, "row2", "C2", "T1", MONDAY, 2, true);
        Occurrence x1 = occ("x1", "x1", "OTHER1", "T1", MONDAY, 1, false);
        Occurrence x2 = occ("x2", "x2", "OTHER2", "T1", MONDAY, 2, false);
        Occurrence t2Busy = occ("p1", "p1", "OTHER3", "T2", MONDAY, 1, false);
        Occurrence t4A = occ("q1", "q1", "OTHER4", "T4", MONDAY.plusDays(1), 1, false);
        Occurrence t4B = occ("q2", "q2", "OTHER5", "T4", MONDAY.plusDays(1), 2, false);
        when(loader.loadPublished(eq(TENANT), any(), any(), anyCollection(), anyCollection(), anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(x1, x2));
        when(loader.loadPublished(eq(TENANT), any(), any(), anyCollection(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(t2Busy, t4A, t4B));
        when(users.findCalendarTeachersByTenantAndSchoolIds(TENANT, List.of(SCHOOL)))
                .thenReturn(List.of(teacher("T1"), teacher("T2"), teacher("T3"), teacher("T4")));
        when(loader.teachersWhoTaughtAt(eq(TENANT), eq(SCHOOL), anyCollection())).thenReturn(Set.of("T2", "T4"));
        when(loader.loadLeaves(anyString(), anyCollection(), any(), any())).thenReturn(Map.of());

        List<ConflictDetailResponse> conflicts = engine.detect(TENANT, List.of(c1, c2), Set.of(), true);

        assertThat(conflicts).hasSize(2);
        ConflictDetailResponse row1 = conflicts.stream().filter(c -> "row1".equals(c.clientRowId())).findFirst().orElseThrow();
        ConflictDetailResponse row2 = conflicts.stream().filter(c -> "row2".equals(c.clientRowId())).findFirst().orElseThrow();
        // period 1: T2 is busy, so the taught-here T4 wins; period 2: T2 and T4 both free and taught here, T2 has the lighter week
        assertThat(row1.fix().teacherId()).isEqualTo("T4");
        assertThat(row2.fix().teacherId()).isEqualTo("T2");
        verify(users, times(1)).findCalendarTeachersByTenantAndSchoolIds(TENANT, List.of(SCHOOL));
        verify(loader, times(1)).teachersWhoTaughtAt(eq(TENANT), eq(SCHOOL), anyCollection());
    }

    @Test
    void withoutSuggestionsNeitherThePoolNorTheBusyListIsLoaded() {
        Occurrence c1 = occ(null, "row1", "C1", "T1", MONDAY, 1, true);
        when(loader.loadPublished(eq(TENANT), any(), any(), anyCollection(), anyCollection(), anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(occ("x1", "x1", "OTHER1", "T1", MONDAY, 1, false)));
        when(loader.loadLeaves(anyString(), anyCollection(), any(), any())).thenReturn(Map.of());

        List<ConflictDetailResponse> conflicts = engine.detect(TENANT, List.of(c1), Set.of(), false);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).fix()).isNull();
        verify(users, never()).findCalendarTeachersByTenantAndSchoolIds(anyString(), anyCollection());
    }
}
