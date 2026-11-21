package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.IntSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention for {@code notification_outbox}: terminal rows ('sent'/'dead'/'failed') accumulate forever, making
 * this the fastest-growing table. Deletes rows older than the retention window in bounded batches so the
 * delete never long-locks the table the worker polls. Non-terminal rows are never touched. Delivery attempts
 * older than the window go first; their outbox_id is ON DELETE SET NULL, so either order is valid.
 *
 * <p>Runs on one instance at a time (cluster advisory lock) and in maintenance tenant scope, because the
 * table is RLS-protected and a scheduler thread has no request tenant.</p>
 */
@Component
public class NotificationOutboxRetentionJob {

    static final String JOB_NAME = "notification-outbox-retention";
    private static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final NotificationOutboxRepository repository;
    private final NotificationDeliveryAttemptRepository attempts;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final ClusterJobLock jobLock;

    public NotificationOutboxRetentionJob(
            NotificationOutboxRepository repository, NotificationDeliveryAttemptRepository attempts,
            boolean enabled, int retentionDays, int batchSize) {
        this(repository, attempts, enabled, retentionDays, batchSize, (ClusterJobLock) null);
    }

    @Autowired
    public NotificationOutboxRetentionJob(
            NotificationOutboxRepository repository,
            NotificationDeliveryAttemptRepository attempts,
            @Value("${erg.notifications.outbox.retention.enabled:true}") boolean enabled,
            @Value("${erg.notifications.outbox.retention.days:90}") int retentionDays,
            @Value("${erg.notifications.outbox.retention.batch-size:1000}") int batchSize,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(repository, attempts, enabled, retentionDays, batchSize, jobLock.getIfAvailable());
    }

    NotificationOutboxRetentionJob(
            NotificationOutboxRepository repository, NotificationDeliveryAttemptRepository attempts,
            boolean enabled, int retentionDays, int batchSize, ClusterJobLock jobLock) {
        this.repository = repository;
        this.attempts = attempts;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.notifications.outbox.retention.cron:0 30 3 * * *}")
    public void purgeExpiredTerminal() {
        if (!enabled) {
            return;
        }
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            if (jobLock == null) {
                purge();
            } else {
                jobLock.runExclusively(JOB_NAME, this::purge);
            }
        }
    }

    private void purge() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int bounded = Math.max(1, batchSize);
        drain(() -> attempts.deleteOlderThan(cutoff, bounded), bounded);
        drain(() -> repository.deleteTerminalOlderThan(cutoff, bounded), bounded);
        drain(() -> repository.deleteFailedOlderThan(cutoff, bounded), bounded);
    }

    /** Batch-by-batch until a partial batch signals the tail, or the time budget is spent (the next run resumes). */
    private static void drain(IntSupplier deleteBatch, int bounded) {
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        // Iteration cap too, so a clock/config mishap can't spin forever in one scheduled run.
        for (int i = 0; i < 10_000 && System.nanoTime() - deadline < 0; i++) {
            if (deleteBatch.getAsInt() < bounded) {
                break;
            }
        }
    }
}
