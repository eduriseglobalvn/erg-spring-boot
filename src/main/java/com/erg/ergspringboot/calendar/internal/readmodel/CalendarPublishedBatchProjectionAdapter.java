package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Calendar-owned adapter that keeps repositories behind the public projection boundary. */
@Component
public class CalendarPublishedBatchProjectionAdapter implements CalendarPublishedBatchProjection {

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository assignments;

    public CalendarPublishedBatchProjectionAdapter(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarEventTeacherRepository assignments
    ) {
        this.batches = batches;
        this.events = events;
        this.assignments = assignments;
    }

    @Override
    public Optional<PublishedBatch> findPublishedBatch(String tenantId, String batchId) {
        Optional<CalendarBatch> batch = batches.findByTenantIdAndId(tenantId, batchId);
        if (batch.isEmpty()) {
            return Optional.empty();
        }
        List<CalendarEvent> calendarEvents = events.findByTenantIdAndBatchId(tenantId, batchId);
        List<String> eventIds = calendarEvents.stream().map(CalendarEvent::getId).toList();
        List<String> teacherIds = eventIds.isEmpty()
                ? List.of()
                : assignments.findByTenantIdAndEventIdIn(tenantId, eventIds).stream()
                        .map(CalendarEventTeacher::getTeacherUserId)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList();
        return Optional.of(new PublishedBatch(batchId, batch.get().getSchoolId(), calendarEvents.size(), teacherIds));
    }
}
