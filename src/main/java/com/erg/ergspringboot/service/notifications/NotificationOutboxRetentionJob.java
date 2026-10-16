package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention for {@code notification_outbox}: terminal rows ('sent'/'dead') accumulate forever, making
 * this the fastest-growing table. Deletes rows older than the retention window in bounded batches so the
 * delete never long-locks the table the worker polls. Non-terminal rows are never touched.
 */
@Component
public class NotificationOutboxRetentionJob {

    private final NotificationOutboxRepository repository;
    private final boolean enabled;
    private final int retentionDays;
    private final int batchSize;

    public NotificationOutboxRetentionJob(
            NotificationOutboxRepository repository,
            @Value("${erg.notifications.outbox.retention.enabled:true}") boolean enabled,
            @Value("${erg.notifications.outbox.retention.days:90}") int retentionDays,
            @Value("${erg.notifications.outbox.retention.batch-size:1000}") int batchSize) {
        this.repository = repository;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${erg.notifications.outbox.retention.cron:0 30 3 * * *}")
    public void purgeExpiredTerminal() {
        if (!enabled) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int bounded = Math.max(1, batchSize);
        // Loop batch-by-batch until a partial batch signals the tail; cap iterations so a clock/config
        // mishap can't spin forever in one scheduled run.
        for (int i = 0; i < 10_000; i++) {
            int deleted = repository.deleteTerminalOlderThan(cutoff, bounded);
            if (deleted < bounded) {
                break;
            }
        }
    }
}
