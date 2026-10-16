package com.erg.ergspringboot.calendar.internal.command;

import com.erg.ergspringboot.calendar.CalendarCommandPort;
import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkCreateUseCase;
import com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkPreviewUseCase;
import com.erg.ergspringboot.calendar.internal.mutation.CalendarEventMutationUseCase;
import com.erg.ergspringboot.calendar.internal.room.CalendarRoomCommandUseCase;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.*;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Composes calendar command use cases behind the module command port. */
@Service
@Primary
public class CalendarCommandUseCase implements CalendarCommandPort {
    private final CalendarBatchLifecycleUseCase batchLifecycle;
    private final CalendarEventMutationUseCase eventMutations;
    private final CalendarRoomCommandUseCase roomCommands;
    private final CalendarBulkPreviewUseCase bulkPreview;
    private final CalendarBulkCreateUseCase bulkCreate;

    public CalendarCommandUseCase(CalendarBatchLifecycleUseCase batchLifecycle,
            CalendarEventMutationUseCase eventMutations, CalendarRoomCommandUseCase roomCommands,
            CalendarBulkPreviewUseCase bulkPreview, CalendarBulkCreateUseCase bulkCreate) {
        this.batchLifecycle = batchLifecycle;
        this.eventMutations = eventMutations;
        this.roomCommands = roomCommands;
        this.bulkPreview = bulkPreview;
        this.bulkCreate = bulkCreate;
    }

    @Override public CalendarPreviewPayload preview(String tenantId, CalendarBulkCreateRequest input, String userId) { return bulkPreview.preview(tenantId, input, userId); }
    @Override public String payloadHash(CalendarBulkCreateRequest input) { return bulkCreate.payloadHash(input); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBulkPayload create(String tenantId, CalendarBulkCreateRequest input, String userId) { return bulkCreate.create(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBatchPayload publishBatch(String tenantId, String batchId, String userId) { return batchLifecycle.publish(tenantId, batchId, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarBatchPayload cancelBatch(String tenantId, String batchId, String userId) { return batchLifecycle.cancel(tenantId, batchId, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarEventPayload updateEvent(String tenantId, CalendarEventUpdateRequest input, String userId) { return eventMutations.update(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarDeletePayload deleteEvent(String tenantId, CalendarEventDeleteRequest input, String userId) { return eventMutations.delete(tenantId, input, userId); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarDeletePayload deleteTeacherSchedule(String tenantId, String teacherUserId, List<String> schoolIds, String dateFrom, String dateTo, String cancelledBy) { return eventMutations.deleteTeacherSchedule(tenantId, teacherUserId, schoolIds, dateFrom, dateTo, cancelledBy); }
    @Override @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true) public CalendarRoomOption createRoom(String tenantId, String schoolId, String roomName, String createdBy) { return roomCommands.create(tenantId, schoolId, roomName, createdBy); }
}
