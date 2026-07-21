package com.erg.ergspringboot.calendar.internal.mutation;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarColorGroupResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherAssignmentResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns transactional mutations of individual calendar events and teacher schedules. */
@Service
public class CalendarEventMutationUseCase {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;

    public CalendarEventMutationUseCase(
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers) {
        this.events = events;
        this.eventTeachers = eventTeachers;
    }

    @Transactional("postgresTransactionManager")
    public CalendarEventPayload update(
            String tenantId, CalendarEventUpdateRequest input, String actorId) {
        CalendarEvent event = findEvent(input.eventId());
        assertTenant(event.getTenantId(), tenantId);
        if (input.roomId() != null) event.setRoomId(input.roomId());
        if (input.roomName() != null) event.setRoomName(input.roomName());
        if (input.note() != null) event.setNote(input.note());
        event.setUpdatedBy(actorId);
        events.save(event);
        return new CalendarEventPayload(toResponse(event));
    }

    @Transactional("postgresTransactionManager")
    public CalendarDeletePayload delete(
            String tenantId, CalendarEventDeleteRequest input, String actorId) {
        CalendarEvent event = findEvent(input.eventId());
        assertTenant(event.getTenantId(), tenantId);
        events.updateStatusBatch(
                tenantId, List.of(event.getId()), CANCELLED, actorId, OffsetDateTime.now());
        return new CalendarDeletePayload(event.getId(), true, 1);
    }

    @Transactional("postgresTransactionManager")
    public CalendarDeletePayload deleteTeacherSchedule(
            String tenantId,
            String teacherUserId,
            List<String> schoolIds,
            String dateFrom,
            String dateTo,
            String actorId) {
        LocalDate fromDate = LocalDate.parse(dateFrom);
        LocalDate toDate = LocalDate.parse(dateTo);
        OffsetDateTime from = fromDate.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime to = toDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();

        Set<String> eventIds = new HashSet<>();
        eventIds.addAll(eventTeachers.findEventIdsByTeacher(
                tenantId, teacherUserId, schoolIds, from, to, DRAFT));
        eventIds.addAll(eventTeachers.findEventIdsByTeacher(
                tenantId, teacherUserId, schoolIds, from, to, PUBLISHED));
        if (eventIds.isEmpty()) {
            return new CalendarDeletePayload(teacherUserId, true, 0);
        }

        int affected = events.updateStatusBatch(
                tenantId, new ArrayList<>(eventIds), CANCELLED, actorId, OffsetDateTime.now());
        return new CalendarDeletePayload(teacherUserId, true, affected);
    }

    private CalendarEvent findEvent(String eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (!Objects.equals(actualTenantId, expectedTenantId)) {
            throw new ForbiddenException("Tenant scope does not match the target schedule resource.");
        }
    }

    private CalendarEventResponse toResponse(CalendarEvent event) {
        List<CalendarTeacherAssignmentResponse> teachers = eventTeachers
                .findByTenantIdAndEventIdIn(event.getTenantId(), List.of(event.getId())).stream()
                .map(teacher -> new CalendarTeacherAssignmentResponse(
                        teacher.getTeacherUserId(),
                        teacher.getDisplayNameSnapshot(),
                        teacher.getRole(),
                        teacher.getColorKey()))
                .toList();

        return new CalendarEventResponse(
                event.getId(), event.getBatchId(), event.getTitle(),
                event.getStartAt() != null ? event.getStartAt().toString() : null,
                event.getEndAt() != null ? event.getEndAt().toString() : null,
                event.getStartAt() != null ? event.getStartAt().toLocalDate().toString() : null,
                event.getStartAt() != null
                        ? event.getStartAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
                event.getEndAt() != null
                        ? event.getEndAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
                event.getSchoolId(), null,
                event.getAcademicClassId(), null,
                event.getSubjectId(), null,
                event.getLevelId(), null,
                event.getRoomId(), event.getRoomName(),
                event.getPeriodStart() != null ? event.getPeriodStart() : 0,
                event.getPeriodCount() != null ? event.getPeriodCount() : 0,
                teachers, event.getStatus(), event.getNote(), colorGroup(event.getSubjectId()));
    }

    private CalendarColorGroupResponse colorGroup(String subjectId) {
        if (subjectId == null) {
            return new CalendarColorGroupResponse("default", null, "subject");
        }
        List<String> colorKeys = List.of(
                "blue", "green", "orange", "purple", "red", "teal", "pink", "indigo");
        int index = Math.abs(subjectId.hashCode()) % colorKeys.size();
        return new CalendarColorGroupResponse(colorKeys.get(index), null, "subject");
    }
}
