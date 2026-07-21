package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarLaneSummaryResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPermissionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.service.calendar.CalendarReadService.ReferenceMaps;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns LCMS, teacher and student Calendar workspace query orchestration. */
public final class CalendarWorkspaceQueryUseCase {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> DEFAULT_STATUSES = List.of("DRAFT", "PUBLISHED");
    private static final String FILTER_SENTINEL = "__none__";
    private static final int MAX_WORKSPACE_DAYS = 31;
    private static final int MAX_FILTER_VALUES = 100;

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final StudentRepository students;
    private final ReferenceLoader references;
    private final CatalogLoader catalogs;
    private final EventMapper eventMapper;
    private final FilterMapper filterMapper;
    private final LaneMapper laneMapper;
    private final PermissionMapper permissionMapper;

    public CalendarWorkspaceQueryUseCase(
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers,
            StudentRepository students,
            ReferenceLoader references,
            CatalogLoader catalogs,
            EventMapper eventMapper,
            FilterMapper filterMapper,
            LaneMapper laneMapper,
            PermissionMapper permissionMapper) {
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.students = students;
        this.references = references;
        this.catalogs = catalogs;
        this.eventMapper = eventMapper;
        this.filterMapper = filterMapper;
        this.laneMapper = laneMapper;
        this.permissionMapper = permissionMapper;
    }

    public CalendarWorkspaceResponse lcms(
            String tenantId, List<String> schoolIds, String from, String to, String viewMode,
            List<String> teacherIds, List<String> classIds, List<String> subjectIds,
            List<String> levelIds, List<String> statuses, ErgPrincipal principal) {
        OffsetDateTime fromDateTime = dayStart(from);
        OffsetDateTime toDateTime = dayAfter(to);
        validateBounds(from, to, schoolIds, teacherIds, classIds, subjectIds, levelIds);
        List<String> effectiveStatuses = hasValues(statuses) ? statuses : DEFAULT_STATUSES;
        List<CalendarEvent> filteredEvents = events.findLcmsWorkspaceEvents(
                tenantId, schoolIds, toDateTime, fromDateTime, effectiveStatuses,
                hasValues(teacherIds), queryValues(teacherIds),
                hasValues(classIds), queryValues(classIds),
                hasValues(subjectIds), queryValues(subjectIds),
                hasValues(levelIds), queryValues(levelIds));
        return response(
                tenantId, from, to, viewMode, schoolIds,
                fromDateTime, toDateTime, filteredEvents, principal);
    }

    public CalendarWorkspaceResponse teacher(
            String tenantId, String teacherUserId, String from, String to, String viewMode,
            List<String> schoolIds, List<String> classIds, List<String> subjectIds,
            List<String> levelIds, List<String> statuses, ErgPrincipal principal) {
        OffsetDateTime fromDateTime = dayStart(from);
        OffsetDateTime toDateTime = dayAfter(to);
        validateBounds(from, to, null, null, classIds, subjectIds, levelIds);
        List<CalendarEvent> filteredEvents = events.findTeacherCalendarEventsFiltered(
                tenantId, teacherUserId, toDateTime, fromDateTime, List.of("PUBLISHED"),
                hasValues(schoolIds), queryValues(schoolIds),
                hasValues(classIds), queryValues(classIds),
                hasValues(subjectIds), queryValues(subjectIds),
                hasValues(levelIds), queryValues(levelIds));
        Set<String> effectiveSchoolIds = hasValues(schoolIds)
                ? new HashSet<>(schoolIds)
                : filteredEvents.stream().map(CalendarEvent::getSchoolId).collect(Collectors.toSet());
        return response(
                tenantId, from, to, viewMode, new ArrayList<>(effectiveSchoolIds),
                fromDateTime, toDateTime, filteredEvents, principal);
    }

    public CalendarWorkspaceResponse student(
            String tenantId, String studentUserId, String from, String to, String viewMode,
            List<String> subjectIds, List<String> levelIds, List<String> statuses,
            ErgPrincipal principal) {
        Student student = students.findFirstByTenantIdAndAuthUserId(tenantId, studentUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Student record not found for userId: " + studentUserId));
        String academicClassId = student.getAcademicClassId();
        if (academicClassId == null) {
            throw new IllegalArgumentException(
                    "Student " + studentUserId + " has no academic class assignment.");
        }
        OffsetDateTime fromDateTime = dayStart(from);
        OffsetDateTime toDateTime = dayAfter(to);
        validateBounds(from, to, null, null, null, subjectIds, levelIds);
        List<CalendarEvent> filteredEvents = events.findStudentCalendarEventsFiltered(
                tenantId, academicClassId, toDateTime, fromDateTime, List.of("PUBLISHED"),
                hasValues(subjectIds), queryValues(subjectIds),
                hasValues(levelIds), queryValues(levelIds));
        List<String> schoolIds = filteredEvents.stream().map(CalendarEvent::getSchoolId)
                .distinct().toList();
        return response(
                tenantId, from, to, viewMode, schoolIds,
                fromDateTime, toDateTime, filteredEvents, principal);
    }

    private CalendarWorkspaceResponse response(
            String tenantId, String from, String to, String viewMode, List<String> schoolIds,
            OffsetDateTime fromDateTime, OffsetDateTime toDateTime,
            List<CalendarEvent> filteredEvents, ErgPrincipal principal) {
        List<CalendarEventTeacher> assignments = loadAssignments(tenantId, filteredEvents);
        Map<String, List<CalendarEventTeacher>> assignmentsByEvent = assignments.stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));
        ReferenceMaps referenceMaps = references.load(tenantId, schoolIds, filteredEvents, assignments);
        List<CalendarEventResponse> responses = filteredEvents.stream()
                .map(event -> eventMapper.map(
                        event, assignmentsByEvent.getOrDefault(event.getId(), List.of()), referenceMaps))
                .toList();
        CalendarFilterOptionsResponse filters = filterMapper.map(
                filteredEvents, assignmentsByEvent, referenceMaps, viewMode);
        List<CalendarLaneSummaryResponse> lanes = laneMapper.map(
                filteredEvents, assignmentsByEvent, referenceMaps, viewMode);
        CalendarCatalogResponse catalog = catalogs.load(
                tenantId, schoolIds, fromDateTime, toDateTime);
        CalendarPermissionsResponse permissions = permissionMapper.map(principal);
        return new CalendarWorkspaceResponse(
                tenantId, from, to, viewMode, responses, filters, lanes, catalog, permissions,
                OffsetDateTime.now().toString());
    }

    private List<CalendarEventTeacher> loadAssignments(
            String tenantId, List<CalendarEvent> filteredEvents) {
        List<String> eventIds = filteredEvents.stream().map(CalendarEvent::getId).toList();
        return eventIds.isEmpty() ? List.of()
                : eventTeachers.findByTenantIdAndEventIdIn(tenantId, eventIds);
    }

    private static boolean hasValues(List<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private static List<String> queryValues(List<String> values) {
        if (!hasValues(values)) return List.of(FILTER_SENTINEL);
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static OffsetDateTime dayStart(String date) {
        return LocalDate.parse(date).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime dayAfter(String date) {
        return LocalDate.parse(date).plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static void validateBounds(
            String from, String to, List<String> schoolIds, List<String> teacherIds,
            List<String> classIds, List<String> subjectIds, List<String> levelIds) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(from);
            end = LocalDate.parse(to);
        } catch (RuntimeException error) {
            throw new BadRequestException("calendar from/to must be ISO dates");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > MAX_WORKSPACE_DAYS) {
            throw new BadRequestException(
                    "calendar date range must be between 1 and " + MAX_WORKSPACE_DAYS + " days");
        }
        for (List<String> values : Arrays.asList(
                schoolIds, teacherIds, classIds, subjectIds, levelIds)) {
            if (values != null && values.stream()
                    .filter(value -> value != null && !value.isBlank()).distinct().count()
                    > MAX_FILTER_VALUES) {
                throw new BadRequestException(
                        "calendar filter list exceeds " + MAX_FILTER_VALUES + " values");
            }
        }
    }

    @FunctionalInterface
    public interface ReferenceLoader {
        ReferenceMaps load(String tenantId, List<String> schoolIds,
                List<CalendarEvent> events, List<CalendarEventTeacher> assignments);
    }

    @FunctionalInterface
    public interface CatalogLoader {
        CalendarCatalogResponse load(
                String tenantId, List<String> schoolIds, OffsetDateTime from, OffsetDateTime to);
    }

    @FunctionalInterface
    public interface EventMapper {
        CalendarEventResponse map(
                CalendarEvent event, List<CalendarEventTeacher> teachers, ReferenceMaps references);
    }

    @FunctionalInterface
    public interface FilterMapper {
        CalendarFilterOptionsResponse map(
                List<CalendarEvent> events,
                Map<String, List<CalendarEventTeacher>> assignments,
                ReferenceMaps references,
                String viewMode);
    }

    @FunctionalInterface
    public interface LaneMapper {
        List<CalendarLaneSummaryResponse> map(
                List<CalendarEvent> events,
                Map<String, List<CalendarEventTeacher>> assignments,
                ReferenceMaps references,
                String viewMode);
    }

    @FunctionalInterface
    public interface PermissionMapper {
        CalendarPermissionsResponse map(ErgPrincipal principal);
    }
}
