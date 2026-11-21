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
    private final CalendarEventMutationUseCase useCase = new CalendarEventMutationUseCase(events, links,
            mock(CalendarReadPort.class), conflicts, mock(ApplicationEventPublisher.class));

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
    void aPlatformWideBulkDeleteWithNoSchoolListReadsEverySchoolInsteadOfAnEmptyInList() {
        when(links.findEventIdsByTeacherInAnySchool(eq("t1"), eq("u1"), any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq("PUBLISHED"))).thenReturn(List.of("ev9"));
        when(events.findAllById(any())).thenReturn(List.of());
        when(events.cancelByIds(eq("t1"), eq(List.of("ev9")), eq("ADMIN_CANCEL"), eq("admin"), any())).thenReturn(1);

        var result = useCase.deleteTeacherSchedule("t1", "u1", List.of(), "2026-12-01", "2026-12-31", "admin");

        assertThat(result.affectedEventCount()).isEqualTo(1);
        verify(links, never()).findEventIdsByTeacher(any(), any(), any(), any(), any(), any());
    }
}
