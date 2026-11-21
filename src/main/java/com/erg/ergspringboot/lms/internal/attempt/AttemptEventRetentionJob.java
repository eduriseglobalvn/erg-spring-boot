package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes {@code attempt_events} older than the retention window (default 180 days) in small batches within a time budget. Nothing
 * reads the events back (grading and review use attempt_answers), and the cutoff is the server receive time, so an offline batch
 * synced late is kept for the full window. One instance runs it (cluster lock); the next run resumes where the budget ended.
 */
@Component
public class AttemptEventRetentionJob {
    static final String JOB_NAME = "lms-attempt-event-retention";
    private static final Logger log = LoggerFactory.getLogger(AttemptEventRetentionJob.class);
    private static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ClusterJobLock jobLock;
    private final int retentionDays;
    private final int batchSize;

    @Autowired
    public AttemptEventRetentionJob(JdbcTemplate jdbc,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager,
            ObjectProvider<ClusterJobLock> jobLock,
            @Value("${erg.lms.attempt-events.retention-days:180}") int retentionDays,
            @Value("${erg.lms.attempt-events.retention-batch-size:1000}") int batchSize) {
        this(jdbc, transactionManager, jobLock.getIfAvailable(), retentionDays, batchSize);
    }

    AttemptEventRetentionJob(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ClusterJobLock jobLock,
            int retentionDays, int batchSize) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.jobLock = jobLock;
        this.retentionDays = Math.max(30, retentionDays); // a typo must not purge recent events
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "${erg.lms.attempt-events.retention-cron:0 40 4 * * *}")
    public void deleteOldEvents() {
        // The table is RLS-protected and a scheduler thread has no request tenant, hence the maintenance scope.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            if (jobLock == null) {
                purge();
            } else {
                jobLock.runExclusively(JOB_NAME, this::purge);
            }
        }
    }

    /** Returns the number of deleted events. */
    long purge() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        long total = 0;
        int deleted;
        // Batch by batch until a partial batch signals the tail, or the budget is spent.
        do {
            Integer count = transaction.execute(status -> jdbc.update(
                    "delete from attempt_events where id in (select id from attempt_events where received_at < ? limit ?)",
                    cutoff, batchSize));
            deleted = count == null ? 0 : count;
            total += deleted;
        } while (deleted >= batchSize && System.nanoTime() - deadline < 0);
        if (total > 0) log.info("ATTEMPT_EVENT_RETENTION deleted={} olderThanDays={}", total, retentionDays);
        return total;
    }
}
