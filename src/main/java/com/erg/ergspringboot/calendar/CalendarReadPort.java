package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchDetailResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPageResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    /**
     * Published periods taught by {@code teacherUserId} on the Asia/Ho_Chi_Minh days {@code from}..{@code to}
     * (inclusive), ordered by start. No principal: the caller has already decided the teacher may be read.
     */
    List<TeacherPeriod> teacherPeriods(String tenantId, String teacherUserId, LocalDate from, LocalDate to);

    /**
     * Which of {@code teacherUserIds} teach a published period starting in [{@code from}, {@code to}) — one query for
     * a whole roster. No principal: the caller has already decided the teachers may be read.
     */
    Set<String> teachersWithPeriodStarting(String tenantId, Collection<String> teacherUserIds, Instant from, Instant to);

}
