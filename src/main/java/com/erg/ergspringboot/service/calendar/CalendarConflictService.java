package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.model.calendar.CalendarConflict;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarConflictRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarConflictService {

    private final CalendarEventRepository eventRepository;
    private final CalendarEventTeacherRepository eventTeacherRepository;
    private final CalendarConflictRepository conflictRepository;

    public CalendarConflictService(CalendarEventRepository eventRepository,
                                    CalendarEventTeacherRepository eventTeacherRepository,
                                    CalendarConflictRepository conflictRepository) {
        this.eventRepository = eventRepository;
        this.eventTeacherRepository = eventTeacherRepository;
        this.conflictRepository = conflictRepository;
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
            Map<String, List<CalendarEvent>> existingClassEvents = classIds.isEmpty()
                    ? Map.of()
                    : eventRepository.findClassTimeConflictsInWindow(tenantId, classIds, windowFrom, windowTo, "CANCELLED")
                            .stream()
                            .collect(Collectors.groupingBy(CalendarEvent::getAcademicClassId));

            Set<String> schoolIds = datedEvents.stream()
                    .filter(CalendarConflictService::hasRoom)
                    .map(CalendarEvent::getSchoolId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<CalendarEvent> existingRoomEvents = schoolIds.isEmpty()
                    ? List.of()
                    : eventRepository.findRoomTimeConflictsInWindow(tenantId, schoolIds, windowFrom, windowTo, "CANCELLED");

            Set<String> teacherIds = teacherEventIds.keySet();
            List<CalendarEventTeacher> teacherAssignmentsInWindow = teacherIds.isEmpty()
                    ? List.of()
                    : eventTeacherRepository.findTeacherAssignmentsInWindow(
                            tenantId, teacherIds, windowFrom, windowTo, "CANCELLED");
            Map<String, CalendarEvent> existingTeacherEventMap = teacherAssignmentsInWindow.isEmpty()
                    ? Map.of()
                    : eventRepository.findByTenantIdAndIdInAndStatusNot(tenantId, teacherAssignmentsInWindow.stream()
                                    .map(CalendarEventTeacher::getEventId)
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .toList(), "CANCELLED")
                            .stream()
                            .collect(Collectors.toMap(CalendarEvent::getId, event -> event, (left, right) -> left));
            Map<String, List<CalendarEventTeacher>> existingAssignmentsByTeacher = teacherAssignmentsInWindow.stream()
                    .collect(Collectors.groupingBy(CalendarEventTeacher::getTeacherUserId));

            for (CalendarEvent event : datedEvents) {
                String eventId = event.getId();
                String clientRowId = clientRowIdByEventId.getOrDefault(eventId, eventId);

                for (CalendarEvent existing : existingClassEvents.getOrDefault(
                        event.getAcademicClassId(), List.of())) {
                    if (Objects.equals(existing.getId(), eventId) || !isOverlapping(event, existing)) continue;
                    conflicts.add(new CalendarConflictResponse(
                            "CLASS_TIME_CONFLICT", "ERROR", clientRowId, existing.getId(),
                            "Lop hoc da co lich trung gio: " + event.getAcademicClassId()));
                }

                for (String teacherId : eventTeacherMap.getOrDefault(eventId, List.of())) {
                    for (CalendarEventTeacher assignment : existingAssignmentsByTeacher.getOrDefault(teacherId, List.of())) {
                        CalendarEvent conflictEvent = existingTeacherEventMap.get(assignment.getEventId());
                        if (conflictEvent != null && !Objects.equals(conflictEvent.getId(), eventId)
                                && isOverlapping(event, conflictEvent)) {
                            conflicts.add(new CalendarConflictResponse(
                                    "TEACHER_TIME_CONFLICT", "ERROR", clientRowId,
                                    conflictEvent.getId(), "Giao vien da co lich trung gio"));
                        }
                    }
                }

                if (hasRoom(event)) {
                    for (CalendarEvent existing : existingRoomEvents) {
                        if (!Objects.equals(existing.getId(), eventId)
                                && Objects.equals(existing.getSchoolId(), event.getSchoolId())
                                && existing.getRoomName() != null
                                && existing.getRoomName().equalsIgnoreCase(event.getRoomName())
                                && isOverlapping(event, existing)) {
                            conflicts.add(new CalendarConflictResponse(
                                    "ROOM_TIME_CONFLICT", "WARNING", clientRowId, existing.getId(),
                                    "Phong hoc da co lich su dung: " + event.getRoomName()));
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
                    "Lop hoc co lich trung nhau trong danh sach");
        }

        for (List<CalendarEvent> roomEvents : candidateEvents.stream()
                .filter(CalendarConflictService::hasRoom)
                .collect(Collectors.groupingBy(CalendarConflictService::roomKey))
                .values()) {
            addPairwiseConflicts(roomEvents, conflicts, clientRowIdByEventId, "ROOM_TIME_CONFLICT", "WARNING",
                    "Phong hoc co lich trung nhau trong danh sach");
        }

        for (Map.Entry<String, Set<String>> entry : teacherEventIds.entrySet()) {
            List<CalendarEvent> teacherEvents = entry.getValue().stream()
                    .map(candidateById::get)
                    .filter(Objects::nonNull)
                    .toList();
            addPairwiseConflicts(teacherEvents, conflicts, clientRowIdByEventId, "TEACHER_TIME_CONFLICT", "ERROR",
                    "Giao vien co lich trung nhau trong danh sach");
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
            if (managedSchoolIds != null && !managedSchoolIds.contains(existingEvent.getSchoolId())) continue;

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

    @Transactional("postgresTransactionManager")
    public List<CalendarConflictResponse> persistConflicts(String tenantId, String batchId,
                                                            List<CalendarConflictResponse> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return conflicts;
        }

        List<CalendarConflict> entities = conflicts.stream().map(c -> {
            CalendarConflict entity = new CalendarConflict();
            entity.setTenantId(tenantId);
            entity.setBatchId(batchId);
            entity.setEventId(c.clientRowId());
            entity.setConflictingEventId(c.existingEventId());
            entity.setConflictType(c.code());
            entity.setSeverity(c.severity());
            entity.setMessage(c.message());
            entity.setClientRowId(c.clientRowId());
            entity.setResolved(false);
            return entity;
        }).collect(Collectors.toList());

        conflictRepository.saveAll(entities);

        return conflicts;
    }

    @Transactional("postgresTransactionManager")
    public void markConflictsResolved(String tenantId, String batchId, String resolvedBy) {
        List<CalendarConflict> unresolved = conflictRepository.findByTenantIdAndBatchIdAndResolved(
                tenantId, batchId, false);
        if (unresolved.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (CalendarConflict conflict : unresolved) {
            conflict.setResolved(true);
            conflict.setResolvedBy(resolvedBy);
            conflict.setResolvedAt(now);
        }
        conflictRepository.saveAll(unresolved);
    }
}
