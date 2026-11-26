package com.erg.ergspringboot.calendar.internal.mutation;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.calendar.internal.support.CalendarCancelAnnouncer;
import com.erg.ergspringboot.calendar.internal.support.CalendarExpansionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns transactional mutations of individual calendar events and teacher schedules. */
@Service
public class CalendarEventMutationUseCase {

    private static final ZoneId VIETNAM_ZONE = CalendarExpansionService.VIETNAM_ZONE;
    private static final int MAX_TEACHER_DELETE_RANGE_YEARS = 1;
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final CalendarReadPort reads;
    private final ScheduleConflictService conflicts;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<Clock> clock;

    public CalendarEventMutationUseCase(
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers,
            CalendarReadPort reads,
            ScheduleConflictService conflicts,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<Clock> clock) {
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.reads = reads;
        this.conflicts = conflicts;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional("postgresTransactionManager")
    public CalendarEventPayload update(
            String tenantId, CalendarEventUpdateRequest input, String actorId) {
        CalendarEvent event = findEvent(input.eventId());
        assertTenant(event.getTenantId(), tenantId);
        if (CANCELLED.equals(event.getStatus())) {
            throw TeachingScheduleException.conflict("EVENT_CANCELLED", "Tiết này đã bị hủy, không sửa được.");
        }
        if (input.expectedVersion() != null && event.getRecordVersion() != input.expectedVersion().longValue()) {
            throw TeachingScheduleException.staleVersion();
        }
        // roomId and roomName always agree: an id wins and brings its stored name, a name alone resolves to the school's room
        if (input.roomId() != null) {
            String stored = conflicts.requireRoomOfSchool(tenantId, event.getSchoolId(), input.roomId());
            event.setRoomId(input.roomId());
            if (stored != null) event.setRoomName(stored);
            else if (input.roomName() != null) event.setRoomName(input.roomName());
        } else if (input.roomName() != null) {
            var match = conflicts.roomByName(tenantId, event.getSchoolId(), input.roomName());
            event.setRoomId(match.map(ScheduleConflictService.RoomMatch::id).orElse(null));
            event.setRoomName(match.map(ScheduleConflictService.RoomMatch::name).orElse(input.roomName()));
        }
        if (input.note() != null) event.setNote(input.note());
        event.setUpdatedBy(actorId);
        events.saveAndFlush(event);
        boolean published = PUBLISHED.equals(event.getStatus());
        if (published && (input.roomId() != null || input.roomName() != null)) {
            // a published period must not land in a room another published period holds at the same time
            long blocking = conflicts.checkPublishedEvents(tenantId, event.getBatchId(), Set.of(event.getId())).stream()
                    .filter(conflict -> "ROOM_TIME".equals(conflict.kind())).count();
            if (blocking > 0) {
                throw new TeachingScheduleException(HttpStatus.CONFLICT, "PUBLISH_BLOCKED_BY_CONFLICTS",
                        "Phòng đã có tiết khác vào giờ này.", Map.of("conflictCount", (int) blocking));
            }
        }
        if (published) {
            String eventId = "schedule-event-changed:" + event.getId() + ":" + event.getRecordVersion();
            eventPublisher.publishEvent(new CalendarEventChanged(eventId, tenantId, event.getId(), event.getBatchId(),
                    actorId, OffsetDateTime.now(), 1, event.getRecordVersion()));
        }
        // Reload through the read model so the payload carries school/class/subject names and colorGroup.
        return new CalendarEventPayload(reads.eventDetail(tenantId, event.getId()));
    }

    @Transactional("postgresTransactionManager")
    public CalendarDeletePayload delete(
            String tenantId, CalendarEventDeleteRequest input, String actorId) {
        CalendarEvent event = findEvent(input.eventId());
        assertTenant(event.getTenantId(), tenantId);
        if (CANCELLED.equals(event.getStatus())) {
            return new CalendarDeletePayload(event.getId(), true, 0);
        }
        // A draft was never on a teacher's sheet; a published period is an admin cancel the teacher should see.
        String reason = DRAFT.equals(event.getStatus()) ? "DRAFT_REMOVED" : "ADMIN_CANCEL";
        int affected = events.cancelByIds(
                tenantId, List.of(event.getId()), reason, actorId, OffsetDateTime.now());
        CalendarCancelAnnouncer.announce(eventPublisher, tenantId, List.of(event), "DELETE", actorId);
        return new CalendarDeletePayload(event.getId(), true, affected);
    }

    @Transactional("postgresTransactionManager")
    public CalendarDeletePayload deleteTeacherSchedule(
            String tenantId,
            String teacherUserId,
            List<String> schoolIds,
            String dateFrom,
            String dateTo,
            String actorId) {
        requireSchoolIds(schoolIds);
        LocalDate fromDate = requiredDate(dateFrom, "dateFrom");
        LocalDate toDate = requiredDate(dateTo, "dateTo");
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("dateFrom must not be after dateTo");
        }
        if (toDate.isAfter(fromDate.plusYears(MAX_TEACHER_DELETE_RANGE_YEARS))) {
            throw new BadRequestException("Teacher schedule delete range must not exceed one year");
        }
        OffsetDateTime from = fromDate.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime to = toDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();

        OffsetDateTime now = OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
        // Owner decision: a published period that has started is history (payroll, teaching hours) and is never deleted,
        // the same rule as cancelling a published batch. Drafts never happened, so all of them go.
        OffsetDateTime publishedFrom = from.isBefore(now) ? now : from;
        Set<String> draftIds = new HashSet<>(teacherEventIds(tenantId, teacherUserId, schoolIds, from, to, DRAFT));
        Set<String> publishedIds = publishedFrom.isBefore(to)
                ? new HashSet<>(teacherEventIds(tenantId, teacherUserId, schoolIds, publishedFrom, to, PUBLISHED))
                : Set.of();
        if (draftIds.isEmpty() && publishedIds.isEmpty()) {
            return new CalendarDeletePayload(teacherUserId, true, 0);
        }

        int affected = 0;
        if (!draftIds.isEmpty()) {
            affected += events.cancelByIds(tenantId, new ArrayList<>(draftIds), "DRAFT_REMOVED", actorId, now);
        }
        if (!publishedIds.isEmpty()) {
            List<CalendarEvent> before = events.findAllById(publishedIds);
            affected += events.cancelByIds(tenantId, new ArrayList<>(publishedIds), "ADMIN_CANCEL", actorId, now);
            CalendarCancelAnnouncer.announce(eventPublisher, tenantId, before, "DELETE", actorId);
        }
        return new CalendarDeletePayload(teacherUserId, true, affected);
    }

    private List<String> teacherEventIds(String tenantId, String teacherUserId, List<String> schoolIds, OffsetDateTime from,
            OffsetDateTime to, String status) {
        return eventTeachers.findEventIdsByTeacher(tenantId, teacherUserId, schoolIds, from, to, status);
    }

    /** Owner decision: the schools are always named, even by a tenant-wide caller; the controllers call this before scope resolution. */
    public static void requireSchoolIds(List<String> schoolIds) {
        if (schoolIds == null || schoolIds.stream().noneMatch(id -> id != null && !id.isBlank())) {
            throw new BadRequestException("Phải chọn ít nhất một trường (schoolIds) để xóa lịch dạy của giáo viên.");
        }
    }

    private CalendarEvent findEvent(String eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> TeachingScheduleException.notFound("Event not found: " + eventId));
    }

    private static LocalDate requiredDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException error) {
            throw new BadRequestException(field + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (!Objects.equals(actualTenantId, expectedTenantId)) {
            throw new ForbiddenException("Tenant scope does not match the target schedule resource.");
        }
    }

}
