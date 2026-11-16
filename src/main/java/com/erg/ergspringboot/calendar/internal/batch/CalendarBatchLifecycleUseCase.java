package com.erg.ergspringboot.calendar.internal.batch;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService;
import com.erg.ergspringboot.calendar.internal.schedule.TeachingScheduleDraftService;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the publish / cancel / rename transaction boundary of teaching-schedule batches (all under a row lock). */
@Service
public class CalendarBatchLifecycleUseCase {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String CANCELLED = "CANCELLED";
    static final int MAX_LABEL = 80;

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final ScheduleConflictService conflicts;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<Clock> clock;

    public CalendarBatchLifecycleUseCase(CalendarBatchRepository batches, CalendarEventRepository events,
            ScheduleConflictService conflicts, ApplicationEventPublisher eventPublisher, ObjectProvider<Clock> clock) {
        this.batches = batches;
        this.events = events;
        this.conflicts = conflicts;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Legacy REST shape: no version check. */
    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload publish(String tenantId, String batchId, String actorId) {
        BatchResponse result = publishBatch(tenantId, batchId, null, actorId);
        return new CalendarBatchPayload(batchId, result.status(), result.affectedEventCount());
    }

    @Transactional("postgresTransactionManager")
    public CalendarBatchPayload cancel(String tenantId, String batchId, String actorId) {
        BatchResponse result = cancelBatch(tenantId, batchId, null, actorId);
        return new CalendarBatchPayload(batchId, result.status(), result.affectedEventCount());
    }

    @Transactional("postgresTransactionManager")
    public BatchResponse publishBatch(String tenantId, String batchId, Integer expectedVersion, String actorId) {
        // two batches of a tenant published at once would each pass the check below (the engine sees PUBLISHED only)
        batches.advisoryXactLock("teaching-schedule-publish:" + tenantId);
        CalendarBatch batch = lock(tenantId, batchId, expectedVersion);
        rejectCancelled(batch);
        if (!DRAFT.equals(batch.getStatus())) {
            throw TeachingScheduleException.conflict("BATCH_NOT_DRAFT", "Đợt lịch này đã được xuất bản.");
        }
        if (events.countByTenantIdAndBatchIdAndStatus(tenantId, batchId, DRAFT) == 0) {
            throw TeachingScheduleException.badRequest("BATCH_EMPTY", "Đợt lịch chưa có tiết nào để xuất bản.");
        }
        List<ConflictDetailResponse> blocking = conflicts.checkStoredDraft(tenantId, batchId, false).stream()
                .filter(conflict -> "ERROR".equals(conflict.severity())).toList();
        if (!blocking.isEmpty()) {
            throw new TeachingScheduleException(HttpStatus.CONFLICT, "PUBLISH_BLOCKED_BY_CONFLICTS",
                    "Còn " + blocking.size() + " xung đột phải xử lý trước khi xuất bản.",
                    Map.of("conflictCount", blocking.size()));
        }

        OffsetDateTime now = now();
        batch.setStatus(PUBLISHED);
        batch.setPublishedBy(actorId);
        batch.setPublishedAt(now);
        batches.saveAndFlush(batch);
        int affected = events.updateStatusByBatchFromStatus(tenantId, batchId, DRAFT, PUBLISHED, actorId, now);
        eventPublisher.publishEvent(new CalendarBatchPublished("schedule-published:" + batchId, tenantId, batchId,
                actorId, now, 1, batchId, batch.getRecordVersion(), "schedule-published:" + batchId, null));
        return TeachingScheduleDraftService.toResponse(batch, affected);
    }

    /** A DRAFT batch cancels every event; a PUBLISHED one only the periods that have not started yet. */
    @Transactional("postgresTransactionManager")
    public BatchResponse cancelBatch(String tenantId, String batchId, Integer expectedVersion, String actorId) {
        CalendarBatch batch = lock(tenantId, batchId, expectedVersion);
        rejectCancelled(batch);
        OffsetDateTime now = now();
        boolean wasDraft = DRAFT.equals(batch.getStatus());
        batch.setStatus(CANCELLED);
        batches.saveAndFlush(batch);
        int affected = wasDraft ? events.cancelAllByBatch(tenantId, batchId, actorId, now)
                : events.cancelFutureByBatch(tenantId, batchId, now, actorId, now);
        eventPublisher.publishEvent(new CalendarBatchCancelled("schedule-cancelled:" + batchId, tenantId, batchId,
                actorId, now, 1, affected, batch.getRecordVersion()));
        return TeachingScheduleDraftService.toResponse(batch, affected);
    }

    /** Name only: no event, no notification. */
    @Transactional("postgresTransactionManager")
    public BatchResponse renameBatch(String tenantId, String batchId, String label, Integer expectedVersion) {
        CalendarBatch batch = lock(tenantId, batchId, expectedVersion);
        rejectCancelled(batch);
        String clean = label == null ? "" : label.trim();
        if (clean.isEmpty()) {
            throw TeachingScheduleException.badRequest("BATCH_LABEL_EMPTY", "Tên đợt không được để trống.");
        }
        if (clean.length() > MAX_LABEL) {
            throw TeachingScheduleException.badRequest("BATCH_LABEL_TOO_LONG", "Tên đợt tối đa " + MAX_LABEL + " ký tự.");
        }
        batch.setLabel(clean);
        try {
            batches.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            throw TeachingScheduleException.conflict("BATCH_LABEL_TAKEN", "Trường này đã có đợt tên \"" + clean + "\".");
        }
        return TeachingScheduleDraftService.toResponse(batch, 0);
    }

    /** SELECT ... FOR UPDATE, then the optional optimistic check against the version the client last saw. */
    CalendarBatch lock(String tenantId, String batchId, Integer expectedVersion) {
        CalendarBatch batch = batches.lockByTenantIdAndId(tenantId, batchId)
                .orElseThrow(() -> TeachingScheduleException.notFound("Batch not found: " + batchId));
        if (expectedVersion != null && batch.getRecordVersion() != expectedVersion.longValue()) {
            throw TeachingScheduleException.staleVersion();
        }
        return batch;
    }

    private static void rejectCancelled(CalendarBatch batch) {
        if (CANCELLED.equals(batch.getStatus())) {
            throw TeachingScheduleException.conflict("BATCH_ALREADY_CANCELLED", "Đợt lịch này đã bị hủy.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
    }
}
