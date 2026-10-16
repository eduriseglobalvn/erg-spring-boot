package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import java.util.List;

/** Narrow command port exposed by the calendar application boundary. */
public interface CalendarCommandPort {
    CalendarPreviewPayload preview(String tenantId, CalendarBulkCreateRequest input, String userId);
    String payloadHash(CalendarBulkCreateRequest input);
    CalendarBulkPayload create(String tenantId, CalendarBulkCreateRequest input, String userId);
    CalendarBatchPayload publishBatch(String tenantId, String batchId, String userId);
    CalendarBatchPayload cancelBatch(String tenantId, String batchId, String userId);
    CalendarEventPayload updateEvent(String tenantId, CalendarEventUpdateRequest input, String userId);
    CalendarDeletePayload deleteEvent(String tenantId, CalendarEventDeleteRequest input, String userId);
    CalendarDeletePayload deleteTeacherSchedule(String tenantId, String teacherUserId, List<String> schoolIds,
            String dateFrom, String dateTo, String cancelledBy);
    CalendarRoomOption createRoom(String tenantId, String schoolId, String roomName, String createdBy);
}
