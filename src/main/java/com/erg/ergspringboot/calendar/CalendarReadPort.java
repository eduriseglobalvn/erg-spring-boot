package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchDetailResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPageResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;

/** Public read boundary for the Calendar Modulith module. */
public interface CalendarReadPort {

    CalendarEventResponse eventDetail(String tenantId, String eventId);

    List<CalendarRoomOptionResponse> activeRooms(String tenantId, String schoolId);

    CalendarWorkspaceResponse lcmsTeachingCalendarWorkspace(
            String tenantId,
            List<String> schoolIds,
            String from,
            String to,
            String viewMode,
            List<String> teacherIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal);

    CalendarWorkspaceResponse teacherTeachingCalendarWorkspace(
            String tenantId,
            String teacherUserId,
            String from,
            String to,
            String viewMode,
            List<String> schoolIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal);

    CalendarWorkspaceResponse studentTeachingCalendarWorkspace(
            String tenantId,
            String studentUserId,
            String from,
            String to,
            String viewMode,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal);

    CalendarBatchPageResponse teachingScheduleBatches(
            String tenantId, String schoolId, String status, Integer page, Integer size);

    CalendarBatchDetailResponse teachingScheduleBatch(String tenantId, String batchId);

}
