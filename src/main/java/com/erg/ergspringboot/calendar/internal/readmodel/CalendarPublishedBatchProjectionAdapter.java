package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.calendar.SchoolShortName;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Calendar-owned adapter that keeps repositories behind the public projection boundary. */
@Component
public class CalendarPublishedBatchProjectionAdapter implements CalendarPublishedBatchProjection {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository assignments;
    private final EducationUnitRepository schools;
    private final ClassroomRepository classes;

    public CalendarPublishedBatchProjectionAdapter(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarEventTeacherRepository assignments,
            EducationUnitRepository schools,
            ClassroomRepository classes
    ) {
        this.batches = batches;
        this.events = events;
        this.assignments = assignments;
        this.schools = schools;
        this.classes = classes;
    }

    @Override
    public Optional<PublishedBatch> findPublishedBatch(String tenantId, String batchId) {
        return batches.findByTenantIdAndId(tenantId, batchId).map(batch -> build(tenantId, batch,
                events.findByTenantIdAndBatchId(tenantId, batchId).stream()
                        .filter(event -> PUBLISHED.equals(event.getStatus())).toList()));
    }

    @Override
    public Optional<PublishedBatch> findCancelledBatch(String tenantId, String batchId, OffsetDateTime cancelledAt) {
        return batches.findByTenantIdAndId(tenantId, batchId)
                .filter(batch -> batch.getPublishedAt() != null)
                .map(batch -> build(tenantId, batch,
                        events.findByTenantIdAndBatchId(tenantId, batchId).stream()
                                .filter(event -> CANCELLED.equals(event.getStatus()))
                                .filter(event -> event.getStartAt().isAfter(cancelledAt))
                                // only this batch's own cancel writes BATCH_CANCELLED (V158); single-period cancels write ADMIN_CANCEL
                                .filter(event -> "BATCH_CANCELLED".equals(event.getCancelReason()))
                                .toList()));
    }

    @Override
    public List<PublishedBatch> findCancelledEvents(String tenantId, java.util.Collection<String> calendarEventIds) {
        if (calendarEventIds.isEmpty()) return List.of();
        Map<String, List<CalendarEvent>> byBatch = events.findByTenantIdAndIdIn(tenantId, calendarEventIds).stream()
                .filter(event -> CANCELLED.equals(event.getStatus()))
                .collect(Collectors.groupingBy(CalendarEvent::getBatchId, java.util.TreeMap::new, Collectors.toList()));
        return byBatch.entrySet().stream()
                .flatMap(entry -> batches.findByTenantIdAndId(tenantId, entry.getKey()).stream()
                        .map(batch -> build(tenantId, batch, entry.getValue())))
                .toList();
    }

    @Override
    public Optional<PublishedBatch> findChangedEvent(String tenantId, String calendarEventId) {
        return events.findByTenantIdAndId(tenantId, calendarEventId)
                .flatMap(event -> batches.findByTenantIdAndId(tenantId, event.getBatchId())
                        .map(batch -> build(tenantId, batch,
                                PUBLISHED.equals(event.getStatus()) ? List.of(event) : List.of())));
    }

    @Override
    public Optional<PublishedBatch> findChangedEvents(String tenantId, String batchId, java.util.Collection<String> calendarEventIds) {
        return batches.findByTenantIdAndId(tenantId, batchId).map(batch -> build(tenantId, batch,
                events.findByTenantIdAndIdIn(tenantId, calendarEventIds).stream()
                        .filter(event -> batchId.equals(event.getBatchId()) && PUBLISHED.equals(event.getStatus())).toList()));
    }

    private PublishedBatch build(String tenantId, CalendarBatch batch, List<CalendarEvent> list) {
        EducationUnit school = schools.findById(batch.getSchoolId()).orElse(null);
        String schoolName = school == null || school.getName() == null ? "" : school.getName();
        if (list.isEmpty()) {
            return new PublishedBatch(batch.getId(), batch.getSchoolId(), schoolName,
                    SchoolShortName.of(schoolName), batch.getStatus(), List.of());
        }
        Map<String, List<CalendarEventTeacher>> byEvent = assignments
                .findByTenantIdAndEventIdIn(tenantId, list.stream().map(CalendarEvent::getId).toList()).stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));
        List<String> classIds = list.stream().map(CalendarEvent::getAcademicClassId).distinct().toList();
        Map<String, Classroom> classMap = classes.findAllById(classIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity()));
        List<Slot> slots = list.stream().sorted(Comparator.comparing(CalendarEvent::getStartAt)).map(event -> {
            Classroom classroom = classMap.get(event.getAcademicClassId());
            List<Person> people = byEvent.getOrDefault(event.getId(), List.of()).stream()
                    .filter(t -> t.getTeacherUserId() != null && !t.getTeacherUserId().isBlank())
                    .map(t -> new Person(t.getTeacherUserId(), t.getDisplayNameSnapshot(),
                            "ASSISTANT".equals(t.getRole()) ? "ASSISTANT" : "MAIN"))
                    .toList();
            return new Slot(event.getId(), event.getAcademicClassId(), classroom == null ? "" : classroom.getName(),
                    event.getStartAt(), event.getPeriodStart(), event.getPeriodCount(), event.getRoomName(), people);
        }).toList();
        return new PublishedBatch(batch.getId(), batch.getSchoolId(), schoolName, SchoolShortName.of(schoolName),
                batch.getStatus(), slots);
    }
}
