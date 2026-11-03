package com.erg.ergspringboot.platform.webhook;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention for bot webhook ingress (C13): expired replay receipts and inbox rows older than
 * {@code erg.webhook.inbox-retention} are deleted in bounded batches, on one instance at a time.
 * Without this both tables grow without limit because no inbox processor exists yet.
 */
@Component
public class WebhookRetentionJob {
    static final String JOB_NAME = "webhook-retention";
    private static final int MAX_BATCHES_PER_RUN = 1000;

    private final JdbcTemplate jdbc;
    private final WebhookProperties properties;
    private final ClusterJobLock jobLock;

    @Autowired
    public WebhookRetentionJob(JdbcTemplate jdbc, WebhookProperties properties, ObjectProvider<ClusterJobLock> jobLock) {
        this(jdbc, properties, jobLock.getIfAvailable());
    }

    WebhookRetentionJob(JdbcTemplate jdbc, WebhookProperties properties, ClusterJobLock jobLock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.webhook.retention-cron:0 40 3 * * *}")
    public void scheduledPurge() {
        if (jobLock == null) {
            purge();
        } else {
            jobLock.runExclusively(JOB_NAME, this::purge);
        }
    }

    int purge() {
        int batch = Math.max(1, Math.min(10_000, properties.getRetentionBatchSize()));
        Duration retention = properties.getInboxRetention() == null || properties.getInboxRetention().isNegative()
                ? Duration.ofDays(30) : properties.getInboxRetention();
        int deleted = 0;
        deleted += loop("""
                delete from webhook_receipts
                where ctid in (select ctid from webhook_receipts where expires_at < current_timestamp limit ?)
                """, batch);
        deleted += loop("""
                delete from webhook_inbox
                where id in (
                    select id from webhook_inbox
                    where received_at < current_timestamp - (? * interval '1 millisecond')
                    order by id
                    limit ?)
                """, batch, retention.toMillis());
        return deleted;
    }

    private int loop(String sql, int batch, Object... leadingArgs) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            Object[] args = new Object[leadingArgs.length + 1];
            System.arraycopy(leadingArgs, 0, args, 0, leadingArgs.length);
            args[leadingArgs.length] = batch;
            int deleted = jdbc.update(sql, args);
            total += deleted;
            if (deleted < batch) break;
        }
        return total;
    }
}
