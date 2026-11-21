package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
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
 * Retention for {@code notification_messages}: messages (and, by FK cascade, their recipient and outbox rows) older
 * than the window are deleted in bounded batches within a time budget; the next run resumes. Messages of a campaign
 * that is still a draft, scheduled or sending are kept. One instance at a time (cluster lock), maintenance scope.
 */
@Component
public class NotificationMessageRetentionJob {

    static final String JOB_NAME = "notification-message-retention";
    private static final Duration RUN_BUDGET = Duration.ofSeconds(30);

    private final NotificationMessageRepository repository;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;
    private final ClusterJobLock jobLock;

    @Autowired
    public NotificationMessageRetentionJob(
            NotificationMessageRepository repository,
            @Value("${erg.notifications.message.retention.enabled:true}") boolean enabled,
            @Value("${erg.notifications.message.retention.days:365}") int retentionDays,
            @Value("${erg.notifications.message.retention.batch-size:500}") int batchSize,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(repository, enabled, retentionDays, batchSize, jobLock.getIfAvailable());
    }

    NotificationMessageRetentionJob(NotificationMessageRepository repository, boolean enabled, int retentionDays,
            int batchSize, ClusterJobLock jobLock) {
        this.repository = repository;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.notifications.message.retention.cron:0 25 4 * * *}")
    public void purgeOldMessages() {
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
        drain(() -> repository.deleteOlderThan(cutoff, bounded), bounded);
    }

    /** Batch by batch until a partial batch signals the tail, or the time budget is spent. */
    private static void drain(IntSupplier deleteBatch, int bounded) {
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        for (int i = 0; i < 10_000 && System.nanoTime() - deadline < 0; i++) {
            if (deleteBatch.getAsInt() < bounded) {
                break;
            }
        }
    }
}
