package com.erg.ergspringboot.calendar.internal.batch;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.service.calendar.CalendarConflictService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the publish/cancel transaction boundary for teaching-schedule batches. */
@Service
public class CalendarBatchLifecycleUseCase {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final ObjectProvider<CalendarConflictService> conflictProvider;
    private final CalendarConflictService compatibilityConflicts;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CalendarBatchLifecycleUseCase(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers,
            ObjectProvider<CalendarConflictService> conflictProvider,
            ApplicationEventPublisher eventPublisher) {
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.conflictProvider = conflictProvider;
        this.compatibilityConflicts = null;
        this.eventPublisher = eventPublisher;
    }

    /** Compatibility constructor retained for legacy command-service tests. */
    public CalendarBatchLifecycleUseCase(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers,
            CalendarConflictService conflicts,
            ApplicationEventPublisher eventPublisher) {
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.conflictProvider = null;
        this.compatibilityConflicts = conflicts;
        this.eventPublisher = eventPublisher;
    }

    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload publish(String tenantId, String batchId, String actorId) {
        CalendarBatch batch = findBatch(tenantId, batchId);
        if (!DRAFT.equals(batch.getStatus())) {
            throw new ConflictException(
                    "Cannot publish batch " + batchId + " with status: " + batch.getStatus());
        }

        // Only DRAFT events are published: events cancelled since the batch was drafted
        // (DELETE_CONFLICTING from another batch, single-event delete) must stay cancelled.
        List<CalendarEvent> draftEvents = events.findByTenantIdAndBatchId(tenantId, batchId).stream()
                .filter(event -> DRAFT.equals(event.getStatus()))
                .toList();
        if (draftEvents.isEmpty()) {
            throw new ConflictException("Cannot publish a batch without draft events: " + batchId);
        }
        List<String> eventIds = draftEvents.stream().map(CalendarEvent::getId).toList();
        List<CalendarEventTeacher> assignments = eventTeachers.findByTenantIdAndEventIdIn(tenantId, eventIds);
        List<CalendarConflictResponse> blocking = conflictService()
                .detectConflicts(tenantId, draftEvents, assignments).stream()
                .filter(conflict -> "ERROR".equals(conflict.severity()))
                .toList();
        if (!blocking.isEmpty()) {
            throw new ConflictException("Cannot publish batch " + batchId + ": " + blocking.size()
                    + " schedule conflict(s) appeared since it was drafted.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        batch.setStatus(PUBLISHED);
        batch.setPublishedBy(actorId);
        batch.setPublishedAt(now);
        batches.save(batch);

        int affected = events.updateStatusBatchFromStatus(tenantId, eventIds, DRAFT, PUBLISHED, actorId, now);
        conflictService().markConflictsResolved(batch.getTenantId(), batchId, actorId);
        eventPublisher.publishEvent(new CalendarBatchPublished(
                "schedule-published:" + batchId, tenantId, batchId, actorId, now, 1));
        return new CalendarBatchPayload(batchId, PUBLISHED, affected);
    }

    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload cancel(String tenantId, String batchId, String actorId) {
        CalendarBatch batch = findBatch(tenantId, batchId);
        if (CANCELLED.equals(batch.getStatus())) {
            throw new ConflictException("Batch is already cancelled: " + batchId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        batch.setStatus(CANCELLED);
        batches.save(batch);
        conflictService().markConflictsResolved(batch.getTenantId(), batchId, actorId);
        List<String> eventIds = events.findByTenantIdAndBatchId(tenantId, batchId).stream()
                .map(CalendarEvent::getId)
                .toList();
        int affected = events.updateStatusBatch(tenantId, eventIds, CANCELLED, actorId, now);
        return new CalendarBatchPayload(batchId, CANCELLED, affected);
    }

    private CalendarBatch findBatch(String tenantId, String batchId) {
        return batches.findByTenantIdAndId(tenantId, batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + batchId));
    }

    private CalendarConflictService conflictService() {
        if (compatibilityConflicts != null) return compatibilityConflicts;
        CalendarConflictService available = conflictProvider == null ? null : conflictProvider.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("Calendar conflict service is not available");
        }
        return available;
    }
}
