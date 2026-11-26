package com.erg.ergspringboot.calendar.internal.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService.RoomMatch;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CalendarEventMutationUseCaseTest {

    private final CalendarEventRepository events = mock(CalendarEventRepository.class);
    private final CalendarEventTeacherRepository links = mock(CalendarEventTeacherRepository.class);
    private final ScheduleConflictService conflicts = mock(ScheduleConflictService.class);
    private final CalendarEventMutationUseCase useCase = useCaseAt("2026-11-01T00:00:00Z");

    @SuppressWarnings("unchecked")
    private CalendarEventMutationUseCase useCaseAt(String instant) {
        org.springframework.beans.factory.ObjectProvider<java.time.Clock> clock = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(clock.getIfAvailable(any())).thenReturn(java.time.Clock.fixed(java.time.Instant.parse(instant), java.time.ZoneOffset.UTC));
        return new CalendarEventMutationUseCase(events, links, mock(CalendarReadPort.class), conflicts, mock(ApplicationEventPublisher.class), clock);
    }

    private CalendarEvent published() {
        CalendarEvent event = new CalendarEvent();
        event.setId("ev1");
        event.setTenantId("t1");
        event.setBatchId("b1");
        event.setSchoolId("s1");
        event.setStatus("PUBLISHED");
        event.setRoomId("old");
        event.setRoomName("Old room");
        event.setRecordVersion(1L);
        when(events.findById("ev1")).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void aNameOnlyRoomChangeOfAPublishedPeriodResolvesTheRoomAndRunsTheRoomGate() {
        CalendarEvent event = published();
        when(conflicts.roomByName("t1", "s1", "p.201")).thenReturn(Optional.of(new RoomMatch("r201", "P.201")));
        when(conflicts.checkPublishedEvents("t1", "b1", Set.of("ev1"))).thenReturn(List.of());

        useCase.update("t1", new CalendarEventUpdateRequest("t1", "ev1", null, "p.201", null, null), "admin");

        assertThat(event.getRoomId()).isEqualTo("r201");
        assertThat(event.getRoomName()).isEqualTo("P.201");
        verify(conflicts).checkPublishedEvents("t1", "b1", Set.of("ev1"));
    }

    @Test
    void aFreeTextRoomNameClearsTheStaleRoomId() {
        CalendarEvent event = published();
        when(conflicts.roomByName(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        useCase.update("t1", new CalendarEventUpdateRequest("t1", "ev1", null, "Sân trường", null, null), "admin");

        assertThat(event.getRoomId()).isNull();
        assertThat(event.getRoomName()).isEqualTo("Sân trường");
    }

    @Test
    void aNoteOnlyUpdateStaysOutOfTheRoomGate() {
        published();

        useCase.update("t1", new CalendarEventUpdateRequest("t1", "ev1", null, null, "note", null), "admin");

        verify(conflicts, never()).checkPublishedEvents(any(), any(), any());
    }

    @Test
    void aNameThatCollidesWithAnotherPublishedPeriodIsRefused() {
        published();
        when(conflicts.roomByName("t1", "s1", "P.201")).thenReturn(Optional.of(new RoomMatch("r201", "P.201")));
        when(conflicts.checkPublishedEvents(eq("t1"), eq("b1"), any())).thenReturn(List.of(
                new com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse("c", "ROOM_TIME", "ERROR", null,
                        1, 1, "07:00", "07:45", "2026-12-07", 1, 1, null, null, null, null, null, "m")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.update("t1",
                new CalendarEventUpdateRequest("t1", "ev1", null, "P.201", null, null), "admin"))
                .isInstanceOf(TeachingScheduleException.class).hasFieldOrPropertyWithValue("code", "PUBLISH_BLOCKED_BY_CONFLICTS");
    }

    @Test
    void aBulkDeleteWithNoSchoolListIsRefusedBeforeAnythingIsRead() {
        for (List<String> none : java.util.Arrays.asList(null, List.<String>of(), List.of(" "))) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    useCase.deleteTeacherSchedule("t1", "u1", none, "2026-12-01", "2026-12-31", "admin"))
                    .isInstanceOf(com.erg.ergspringboot.exception.BadRequestException.class);
        }
        verify(links, never()).findEventIdsByTeacher(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aBulkDeleteWithNamedSchoolsCancelsThePublishedPeriods() {
        when(links.findEventIdsByTeacher(eq("t1"), eq("u1"), eq(List.of("s1")), any(OffsetDateTime.class),
                any(OffsetDateTime.class), eq("PUBLISHED"))).thenReturn(List.of("ev9"));
        when(events.findAllById(any())).thenReturn(List.of());
        when(events.cancelByIds(eq("t1"), eq(List.of("ev9")), eq("ADMIN_CANCEL"), eq("admin"), any())).thenReturn(1);

        var result = useCase.deleteTeacherSchedule("t1", "u1", List.of("s1"), "2026-12-01", "2026-12-31", "admin");

        assertThat(result.affectedEventCount()).isEqualTo(1);
    }

    /** Owner decision 26/11/2026: a published period that has started is payroll history; a range delete never reaches it. */
    @Test
    void aBulkDeleteSkipsPublishedPeriodsThatHaveStartedButStillClearsPastDrafts() {
        CalendarEventMutationUseCase midMonth = useCaseAt("2026-12-10T03:00:00Z");

        midMonth.deleteTeacherSchedule("t1", "u1", List.of("s1"), "2026-12-01", "2026-12-31", "admin");

        verify(links).findEventIdsByTeacher("t1", "u1", List.of("s1"), OffsetDateTime.parse("2026-12-10T03:00:00Z"),
                OffsetDateTime.parse("2027-01-01T00:00:00+07:00"), "PUBLISHED");
        verify(links).findEventIdsByTeacher("t1", "u1", List.of("s1"), OffsetDateTime.parse("2026-12-01T00:00:00+07:00"),
                OffsetDateTime.parse("2027-01-01T00:00:00+07:00"), "DRAFT");
    }

    @Test
    void aBulkDeleteOfAWhollyPastRangeNeverLooksForPublishedPeriods() {
        CalendarEventMutationUseCase later = useCaseAt("2027-02-01T00:00:00Z");

        var result = later.deleteTeacherSchedule("t1", "u1", List.of("s1"), "2026-12-01", "2026-12-31", "admin");

        assertThat(result.affectedEventCount()).isZero();
        verify(links, never()).findEventIdsByTeacher(any(), any(), any(), any(), any(), eq("PUBLISHED"));
    }
}
