package com.erg.ergspringboot.reporting.internal;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.reporting.persistence.ReportJobRepository;
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
 * Retention for {@code report_jobs}: a finished job's result (up to 10,000 rows of json) is useless once its download
 * window ({@code expires_at}) closed, yet rows were never removed. Deletes them 30 days after expiry in bounded,
 * time-budgeted batches. One instance at a time (cluster lock), maintenance scope (the table is RLS-protected).
 */
@Component
public class ReportJobRetentionJob {

    static final String JOB_NAME = "report-job-retention";
    private static final Duration RUN_BUDGET = Duration.ofSeconds(30);

    private final ReportJobRepository repository;
    private final boolean enabled;
    private final int graceDays;
    private final int batchSize;
    private final ClusterJobLock jobLock;

    @Autowired
    public ReportJobRetentionJob(
            ReportJobRepository repository,
            @Value("${erg.reporting.jobs.retention.enabled:true}") boolean enabled,
            @Value("${erg.reporting.jobs.retention.days-after-expiry:30}") int graceDays,
            @Value("${erg.reporting.jobs.retention.batch-size:500}") int batchSize,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(repository, enabled, graceDays, batchSize, jobLock.getIfAvailable());
    }

    ReportJobRetentionJob(ReportJobRepository repository, boolean enabled, int graceDays, int batchSize,
            ClusterJobLock jobLock) {
        this.repository = repository;
        this.enabled = enabled;
        this.graceDays = graceDays;
        this.batchSize = batchSize;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.reporting.jobs.retention.cron:0 10 4 * * *}")
    public void purgeExpiredJobs() {
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
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(graceDays);
        int bounded = Math.max(1, batchSize);
        drain(() -> repository.deleteExpiredBefore(cutoff, bounded), bounded);
    }

    /** Batch by batch until a partial batch signals the tail, or the time budget is spent (the next run resumes). */
    private static void drain(IntSupplier deleteBatch, int bounded) {
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        for (int i = 0; i < 10_000 && System.nanoTime() - deadline < 0; i++) {
            if (deleteBatch.getAsInt() < bounded) {
                break;
            }
        }
    }
}
