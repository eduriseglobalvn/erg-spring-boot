package com.erg.ergspringboot.calendar.internal.support;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleDataLoader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Legacy conflict check kept for the bulk preview/create flow of the old schedule dialog. New code uses
 * {@link ScheduleConflictEngine}; remove this class together with the old flow (F4/B10).
 */
@Deprecated
@Service
public class CalendarConflictService {

    private final CalendarEventRepository eventRepository;
    private final CalendarEventTeacherRepository eventTeacherRepository;

    public CalendarConflictService(CalendarEventRepository eventRepository,
                                    CalendarEventTeacherRepository eventTeacherRepository) {
        this.eventRepository = eventRepository;
        this.eventTeacherRepository = eventTeacherRepository;
    }

    public record ResolveResult(
            List<CalendarConflictResponse> resolvedConflicts,
            Set<String> eventIdsToDelete
    ) {}

    public List<CalendarConflictResponse> detectConflicts(
            String tenantId,
            List<CalendarEvent> candidateEvents,
            List<CalendarEventTeacher> candidateTeachers) {
        return detectConflicts(tenantId, candidateEvents, candidateTeachers, Map.of());
    }

    public List<CalendarConflictResponse> detectConflicts(
            String tenantId,
            List<CalendarEvent> candidateEvents,
            List<CalendarEventTeacher> candidateTeachers,
            Map<String, String> clientRowIdByEventId) {
        List<CalendarConflictResponse> conflicts = new ArrayList<>();

        if (candidateEvents == null || candidateEvents.isEmpty()) {
            return conflicts;
        }

        List<CalendarEventTeacher> safeCandidateTeachers =
                candidateTeachers == null ? List.of() : candidateTeachers;

        Map<String, List<String>> eventTeacherMap = new HashMap<>();
        for (CalendarEventTeacher teacher : safeCandidateTeachers) {
            if (teacher == null || teacher.getEventId() == null) continue;
            eventTeacherMap.computeIfAbsent(teacher.getEventId(), k -> new ArrayList<>())
                    .add(teacher.getTeacherUserId());
        }

        Map<String, Set<String>> teacherEventIds = new HashMap<>();
        for (CalendarEventTeacher teacher : safeCandidateTeachers) {
            if (teacher == null || teacher.getEventId() == null || teacher.getTeacherUserId() == null) continue;
            teacherEventIds.computeIfAbsent(teacher.getTeacherUserId(), k -> new HashSet<>())
                    .add(teacher.getEventId());
        }

        Map<String, CalendarEvent> candidateById = candidateEvents.stream()
                .filter(event -> event != null && event.getId() != null)
                .collect(Collectors.toMap(CalendarEvent::getId, event -> event, (left, right) -> left));

        List<CalendarEvent> datedEvents = candidateEvents.stream()
                .filter(event -> event != null && event.getId() != null
                        && event.getStartAt() != null && event.getEndAt() != null)
                .toList();

        if (!datedEvents.isEmpty()) {
            OffsetDateTime windowFrom = datedEvents.stream()
                    .map(CalendarEvent::getStartAt)
                    .min(OffsetDateTime::compareTo)
                    .orElseThrow();
            OffsetDateTime windowTo = datedEvents.stream()
                    .map(CalendarEvent::getEndAt)
                    .max(OffsetDateTime::compareTo)
                    .orElseThrow();

            Set<String> classIds = datedEvents.stream()
                    .map(CalendarEvent::getAcademicClassId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            // every IN list is chunked: a large grid would pass the 32,767 bind-parameter limit of one statement
            Map<String, List<CalendarEvent>> existingClassEvents = ScheduleDataLoader.chunks(classIds).stream()
                    .flatMap(chunk -> eventRepository.findClassTimeConflictsInWindow(
                            tenantId, chunk, windowFrom, windowTo, "CANCELLED").stream())
                    .collect(Collectors.groupingBy(CalendarEvent::getAcademicClassId));

            Set<String> schoolIds = datedEvents.stream()
                    .filter(CalendarConflictService::hasRoom)
                    .map(CalendarEvent::getSchoolId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<CalendarEvent> existingRoomEvents = ScheduleDataLoader.chunks(schoolIds).stream()
                    .flatMap(chunk -> eventRepository.findRoomTimeConflictsInWindow(
                            tenantId, chunk, windowFrom, windowTo, "CANCELLED").stream())
                    .toList();

            Set<String> teacherIds = teacherEventIds.keySet();
            List<CalendarEventTeacher> teacherAssignmentsInWindow = ScheduleDataLoader.chunks(teacherIds).stream()
                    .flatMap(chunk -> eventTeacherRepository.findTeacherAssignmentsInWindow(
                            tenantId, chunk, windowFrom, windowTo, "CANCELLED").stream())
                    .toList();
            List<String> assignedEventIds = teacherAssignmentsInWindow.stream().map(CalendarEventTeacher::getEventId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<String, CalendarEvent> existingTeacherEventMap = ScheduleDataLoader.chunks(assignedEventIds).stream()
                    .flatMap(chunk -> eventRepository.findByTenantIdAndIdInAndStatusNot(tenantId, chunk, "CANCELLED").stream())
                    .collect(Collectors.toMap(CalendarEvent::getId, event -> event, (left, right) -> left));
            Map<String, List<CalendarEventTeacher>> existingAssignmentsByTeacher = teacherAssignmentsInWindow.stream()
                    .collect(Collectors.groupingBy(CalendarEventTeacher::getTeacherUserId));

            Map<String, List<CalendarEvent>> existingRoomsByKey = existingRoomEvents.stream()
                    .filter(CalendarConflictService::hasRoom).collect(Collectors.groupingBy(CalendarConflictService::roomKey));
            for (CalendarEvent event : datedEvents) {
                String eventId = event.getId();
                String clientRowId = clientRowIdByEventId.getOrDefault(eventId, eventId);

                for (CalendarEvent existing : existingClassEvents.getOrDefault(
                        event.getAcademicClassId(), List.of())) {
                    if (Objects.equals(existing.getId(), eventId) || !isOverlapping(event, existing)) continue;
                    conflicts.add(new CalendarConflictResponse(
                            "CLASS_TIME_CONFLICT", "ERROR", clientRowId, existing.getId(),
                            "Lớp học đã có lịch trùng giờ: " + event.getAcademicClassId()));
                }

                for (String teacherId : eventTeacherMap.getOrDefault(eventId, List.of())) {
                    for (CalendarEventTeacher assignment : existingAssignmentsByTeacher.getOrDefault(teacherId, List.of())) {
                        CalendarEvent conflictEvent = existingTeacherEventMap.get(assignment.getEventId());
                        if (conflictEvent != null && !Objects.equals(conflictEvent.getId(), eventId)
                                && isOverlapping(event, conflictEvent)) {
                            conflicts.add(new CalendarConflictResponse(
                                    "TEACHER_TIME_CONFLICT", "ERROR", clientRowId,
                                    conflictEvent.getId(), "Giáo viên đã có lịch trùng giờ"));
                        }
                    }
                }

                if (hasRoom(event)) {
                    for (CalendarEvent existing : existingRoomsByKey.getOrDefault(roomKey(event), List.of())) {
                        if (!Objects.equals(existing.getId(), eventId) && isOverlapping(event, existing)) {
                            conflicts.add(new CalendarConflictResponse(
                                    "ROOM_TIME_CONFLICT", "WARNING", clientRowId, existing.getId(),
                                    "Phòng học đã có lịch sử dụng: " + event.getRoomName()));
                        }
                    }
                }
            }
        }

        for (List<CalendarEvent> classEvents : candidateEvents.stream()
                .filter(event -> event != null && event.getAcademicClassId() != null)
                .collect(Collectors.groupingBy(CalendarEvent::getAcademicClassId))
                .values()) {
            addPairwiseConflicts(classEvents, conflicts, clientRowIdByEventId, "CLASS_TIME_CONFLICT", "ERROR",
                    "Lớp học có lịch trùng nhau trong danh sách");
        }

        for (List<CalendarEvent> roomEvents : candidateEvents.stream()
                .filter(CalendarConflictService::hasRoom)
                .collect(Collectors.groupingBy(CalendarConflictService::roomKey))
                .values()) {
            addPairwiseConflicts(roomEvents, conflicts, clientRowIdByEventId, "ROOM_TIME_CONFLICT", "WARNING",
                    "Phòng học có lịch trùng nhau trong danh sách");
        }

        for (Map.Entry<String, Set<String>> entry : teacherEventIds.entrySet()) {
            List<CalendarEvent> teacherEvents = entry.getValue().stream()
                    .map(candidateById::get)
                    .filter(Objects::nonNull)
                    .toList();
            addPairwiseConflicts(teacherEvents, conflicts, clientRowIdByEventId, "TEACHER_TIME_CONFLICT", "ERROR",
                    "Giáo viên có lịch trùng nhau trong danh sách");
        }

        Map<ConflictKey, CalendarConflictResponse> unique = new LinkedHashMap<>();
        for (CalendarConflictResponse conflict : conflicts) {
            unique.putIfAbsent(new ConflictKey(
                    conflict.code(), conflict.clientRowId(), conflict.existingEventId()), conflict);
        }
        return new ArrayList<>(unique.values());
    }

    private record ConflictKey(String code, String clientRowId, String existingEventId) {}

    private static void addPairwiseConflicts(
            List<CalendarEvent> events,
            List<CalendarConflictResponse> conflicts,
            Map<String, String> clientRowIdByEventId,
            String code,
            String severity,
            String message) {
        for (int i = 0; i < events.size(); i++) {
            CalendarEvent eventA = events.get(i);
            if (eventA == null) continue;
            for (int j = i + 1; j < events.size(); j++) {
                CalendarEvent eventB = events.get(j);
                if (eventB != null && isOverlapping(eventA, eventB)) {
                    conflicts.add(new CalendarConflictResponse(
                            code, severity,
                            clientRowIdByEventId.getOrDefault(eventA.getId(), eventA.getId()),
                            eventB.getId(), message));
                }
            }
        }
    }

    private static boolean hasRoom(CalendarEvent event) {
        return event != null && event.getRoomName() != null && !event.getRoomName().isBlank();
    }

    private static String roomKey(CalendarEvent event) {
        return event.getSchoolId() + "|" + event.getRoomName().trim().toLowerCase();
    }

    public ResolveResult resolveConflictsByDeletion(
            String tenantId,
            List<CalendarConflictResponse> conflicts,
            List<String> managedSchoolIds) {
        List<CalendarConflictResponse> resolvedConflicts = new ArrayList<>();
        Set<String> eventIdsToDelete = new HashSet<>();

        if (conflicts == null || conflicts.isEmpty()) {
            return new ResolveResult(resolvedConflicts, eventIdsToDelete);
        }

        List<String> existingEventIds = conflicts.stream()
                .filter(conflict -> conflict != null && "TEACHER_TIME_CONFLICT".equals(conflict.code()))
                .map(CalendarConflictResponse::existingEventId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (existingEventIds.isEmpty()) {
            return new ResolveResult(resolvedConflicts, eventIdsToDelete);
        }
        Map<String, CalendarEvent> existingEventsById = eventRepository
                .findByTenantIdAndIdIn(tenantId, existingEventIds).stream()
                .collect(Collectors.toMap(CalendarEvent::getId, event -> event, (left, right) -> left));

        for (CalendarConflictResponse conflict : conflicts) {
            if (conflict == null || !"TEACHER_TIME_CONFLICT".equals(conflict.code())) continue;

            String existingEventId = conflict.existingEventId();
            if (existingEventId == null || existingEventId.isBlank()) continue;

            CalendarEvent existingEvent = existingEventsById.get(existingEventId);
            if (existingEvent == null) continue;
            // "*" is the tenant-wide scope AccessScopeAssembler returns for super admin
            if (managedSchoolIds != null && !managedSchoolIds.contains("*")
                    && !managedSchoolIds.contains(existingEvent.getSchoolId())) continue;

            eventIdsToDelete.add(existingEventId);
            resolvedConflicts.add(conflict);
        }

        return new ResolveResult(resolvedConflicts, eventIdsToDelete);
    }

    private static boolean isOverlapping(CalendarEvent a, CalendarEvent b) {
        if (a.getStartAt() == null || a.getEndAt() == null
                || b.getStartAt() == null || b.getEndAt() == null) {
            return false;
        }
        return a.getStartAt().isBefore(b.getEndAt()) && a.getEndAt().isAfter(b.getStartAt());
    }

}
