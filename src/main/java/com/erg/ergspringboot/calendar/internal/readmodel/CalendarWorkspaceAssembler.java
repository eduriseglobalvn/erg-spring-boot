package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarLaneSummaryResponse;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.service.calendar.CalendarReadService.ReferenceMaps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Builds filter and lane projections shared by all Calendar workspace queries. */
@Service
public class CalendarWorkspaceAssembler {

    public CalendarFilterOptionsResponse filterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps references,
            String viewMode) {
        Map<String, Long> schoolCounts = events.stream().collect(Collectors.groupingBy(
                CalendarEvent::getSchoolId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> classCounts = events.stream().collect(Collectors.groupingBy(
                CalendarEvent::getAcademicClassId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> subjectCounts = events.stream().filter(event -> event.getSubjectId() != null)
                .collect(Collectors.groupingBy(
                        CalendarEvent::getSubjectId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> levelCounts = events.stream().filter(event -> event.getLevelId() != null)
                .collect(Collectors.groupingBy(
                        CalendarEvent::getLevelId, LinkedHashMap::new, Collectors.counting()));
        Set<String> eventIds = events.stream().map(CalendarEvent::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Long> teacherCounts = new LinkedHashMap<>();
        for (var entry : assignmentsByEvent.entrySet()) {
            if (!eventIds.contains(entry.getKey())) continue;
            for (CalendarEventTeacher teacher : entry.getValue()) {
                teacherCounts.merge(teacher.getTeacherUserId(), 1L, Long::sum);
            }
        }
        return new CalendarFilterOptionsResponse(
                schoolCounts.entrySet().stream().map(entry -> new CalendarFilterOptionResponse(
                        entry.getKey(), schoolName(entry.getKey(), references), entry.getValue().intValue(), null)).toList(),
                classCounts.entrySet().stream().map(entry -> new CalendarFilterOptionResponse(
                        entry.getKey(), className(entry.getKey(), references), entry.getValue().intValue(), null)).toList(),
                teacherCounts.entrySet().stream().map(entry -> new CalendarFilterOptionResponse(
                        entry.getKey(), teacherName(entry.getKey(), references), entry.getValue().intValue(),
                        teacherColor(entry.getKey(), references))).toList(),
                subjectCounts.entrySet().stream().map(entry -> new CalendarFilterOptionResponse(
                        entry.getKey(), subjectName(entry.getKey(), references), entry.getValue().intValue(), null)).toList(),
                levelCounts.entrySet().stream().map(entry -> new CalendarFilterOptionResponse(
                        entry.getKey(), levelName(entry.getKey(), references), entry.getValue().intValue(), null)).toList());
    }

    public List<CalendarLaneSummaryResponse> laneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps references,
            String viewMode) {
        if (events == null || events.isEmpty()) return List.of();
        String mode = viewMode != null ? viewMode.toUpperCase() : "BY_SCHOOL";
        return switch (mode) {
            case "CLASS", "BY_CLASS" -> summariesByKey(
                    events, CalendarEvent::getAcademicClassId, id -> className(id, references), null);
            case "SUBJECT", "BY_SUBJECT" -> summariesByKey(
                    events, CalendarEvent::getSubjectId, id -> subjectName(id, references), null);
            case "TEACHER", "BY_TEACHER" -> teacherSummaries(events, assignmentsByEvent, references);
            default -> summariesByKey(
                    events, CalendarEvent::getSchoolId, id -> schoolName(id, references), null);
        };
    }

    private List<CalendarLaneSummaryResponse> summariesByKey(
            List<CalendarEvent> events,
            Function<CalendarEvent, String> keyExtractor,
            Function<String, String> labelResolver,
            Function<String, String> colorResolver) {
        Map<String, Long> counts = events.stream().filter(event -> keyExtractor.apply(event) != null)
                .collect(Collectors.groupingBy(
                        keyExtractor, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(entry -> {
            String label = labelResolver.apply(entry.getKey());
            String color = colorResolver == null ? null : colorResolver.apply(entry.getKey());
            return new CalendarLaneSummaryResponse(
                    entry.getKey(), label != null ? label : entry.getKey(), entry.getValue().intValue(), color);
        }).toList();
    }

    private List<CalendarLaneSummaryResponse> teacherSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps references) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CalendarEvent event : events) {
            for (CalendarEventTeacher teacher : assignmentsByEvent.getOrDefault(event.getId(), List.of())) {
                counts.merge(teacher.getTeacherUserId(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream().map(entry -> {
            String label = teacherName(entry.getKey(), references);
            return new CalendarLaneSummaryResponse(
                    entry.getKey(), label != null ? label : entry.getKey(), entry.getValue().intValue(),
                    teacherColor(entry.getKey(), references));
        }).toList();
    }

    private String schoolName(String id, ReferenceMaps references) {
        EducationUnit value = id == null ? null : references.schoolMap().get(id);
        return value == null ? null : value.getName();
    }

    private String className(String id, ReferenceMaps references) {
        Classroom value = id == null ? null : references.classMap().get(id);
        return value == null ? null : value.getName();
    }

    private String subjectName(String id, ReferenceMaps references) {
        Subject value = id == null ? null : references.subjectMap().get(id);
        return value == null ? null : value.getName();
    }

    private String levelName(String id, ReferenceMaps references) {
        Level value = id == null ? null : references.levelMap().get(id);
        return value == null ? null : value.getName();
    }

    private String teacherName(String id, ReferenceMaps references) {
        User value = id == null ? null : references.userMap().get(id);
        return value == null ? null : value.getFullName();
    }

    private String teacherColor(String id, ReferenceMaps references) {
        return id == null ? null : references.teacherColorKeyMap().get(id);
    }
}
