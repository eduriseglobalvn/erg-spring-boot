package com.erg.ergspringboot.calendar.internal.batch;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
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
    private final ObjectProvider<CalendarConflictService> conflictProvider;
    private final CalendarConflictService compatibilityConflicts;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CalendarBatchLifecycleUseCase(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            ObjectProvider<CalendarConflictService> conflictProvider,
            ApplicationEventPublisher eventPublisher) {
        this.batches = batches;
        this.events = events;
        this.conflictProvider = conflictProvider;
        this.compatibilityConflicts = null;
        this.eventPublisher = eventPublisher;
    }

    /** Compatibility constructor retained for legacy command-service tests. */
    public CalendarBatchLifecycleUseCase(
            CalendarBatchRepository batches,
            CalendarEventRepository events,
            CalendarConflictService conflicts,
            ApplicationEventPublisher eventPublisher) {
        this.batches = batches;
        this.events = events;
        this.conflictProvider = null;
        this.compatibilityConflicts = conflicts;
        this.eventPublisher = eventPublisher;
    }

    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload publish(String tenantId, String batchId, String actorId) {
        CalendarBatch batch = findBatch(tenantId, batchId);
        if (!DRAFT.equals(batch.getStatus())) {
            throw new IllegalStateException(
                    "Cannot publish batch " + batchId + " with status: " + batch.getStatus());
        }

        List<CalendarEvent> batchEvents = events.findByTenantIdAndBatchId(tenantId, batchId);
        if (batchEvents.isEmpty()) {
            throw new IllegalStateException("Cannot publish an empty batch: " + batchId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        batch.setStatus(PUBLISHED);
        batch.setPublishedBy(actorId);
        batch.setPublishedAt(now);
        batches.save(batch);

        List<String> eventIds = batchEvents.stream().map(CalendarEvent::getId).toList();
        int affected = events.updateStatusBatch(tenantId, eventIds, PUBLISHED, actorId, now);
        conflictService().markConflictsResolved(batch.getTenantId(), batchId, actorId);
        eventPublisher.publishEvent(new CalendarBatchPublished(
                "schedule-published:" + batchId, tenantId, batchId, actorId, now, 1));
        return new CalendarBatchPayload(batchId, PUBLISHED, affected);
    }

    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload cancel(String tenantId, String batchId, String actorId) {
        CalendarBatch batch = findBatch(tenantId, batchId);
        if (CANCELLED.equals(batch.getStatus())) {
            throw new IllegalStateException("Batch is already cancelled: " + batchId);
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
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
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
