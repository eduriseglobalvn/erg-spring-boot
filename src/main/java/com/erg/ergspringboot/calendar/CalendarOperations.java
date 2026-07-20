package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.calendar.CalendarCommandService;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public facade for calendar use cases. No repository or JPA type is exposed. */
@Service
public class CalendarOperations {
    private final CalendarReadService reads;
    private final CalendarCommandService commands;

    public CalendarOperations(CalendarReadService reads, CalendarCommandService commands) {
        this.reads = reads;
        this.commands = commands;
    }

    public CalendarWorkspaceResponse workspace(String tenantId, List<String> schools, String from, String to,
            String viewMode, List<String> teachers, List<String> classes, List<String> subjects,
            List<String> levels, List<String> statuses, ErgPrincipal principal) {
        return reads.lcmsTeachingCalendarWorkspace(tenantId, schools, from, to, viewMode,
                teachers, classes, subjects, levels, statuses, principal);
    }

    public CalendarWorkspaceResponse teacherWorkspace(String tenantId, String userId, String from, String to,
            String viewMode, List<String> schools, List<String> classes, List<String> subjects,
            List<String> levels, List<String> statuses, ErgPrincipal principal) {
        return reads.teacherTeachingCalendarWorkspace(tenantId, userId, from, to, viewMode,
                schools, classes, subjects, levels, statuses, principal);
    }

    public CalendarEventResponse event(String tenantId, String eventId) { return reads.eventDetail(tenantId, eventId); }
    public CalendarBatchPageResponse batches(String tenantId, String schoolId, String status, int page, int size) {
        return reads.teachingScheduleBatches(tenantId, schoolId, status, page, size);
    }
    public CalendarBatchDetailResponse batch(String tenantId, String batchId) {
        return reads.teachingScheduleBatch(tenantId, batchId);
    }
    public List<CalendarRoomOptionResponse> rooms(String tenantId, String schoolId) { return reads.activeRooms(tenantId, schoolId); }
    public CalendarPreviewPayload preview(String tenantId, CalendarBulkCreateRequest request, String actor) {
        return commands.preview(tenantId, request, actor);
    }
    public CalendarBulkPayload create(String tenantId, CalendarBulkCreateRequest request, String actor) {
        return commands.create(tenantId, request, actor);
    }
    public CalendarEventPayload update(String tenantId, CalendarEventUpdateRequest request, String actor) {
        return commands.updateEvent(tenantId, request, actor);
    }
    public CalendarDeletePayload delete(String tenantId, CalendarEventDeleteRequest request, String actor) {
        return commands.deleteEvent(tenantId, request, actor);
    }
    public CalendarDeletePayload deleteTeacherSchedule(String tenantId, String teacherId, List<String> schools,
            String from, String to, String actor) {
        return commands.deleteTeacherSchedule(tenantId, teacherId, schools, from, to, actor);
    }
    public CalendarBatchPayload publish(String tenantId, String batchId, String actor) {
        return commands.publishBatch(tenantId, batchId, actor);
    }
    public CalendarBatchPayload cancel(String tenantId, String batchId, String actor) {
        return commands.cancelBatch(tenantId, batchId, actor);
    }
    public CalendarRoomOption createRoom(String tenantId, String schoolId, String name, String actor) {
        return commands.createRoom(tenantId, schoolId, name, actor);
    }
}
