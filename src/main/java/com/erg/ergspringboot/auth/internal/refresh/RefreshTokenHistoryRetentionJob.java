package com.erg.ergspringboot.auth.internal.refresh;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.users.persistence.auth.RefreshTokenHistoryRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHistoryRetentionJob {
    static final String JOB_NAME = "refresh-token-history-retention";
    private static final int BATCH_SIZE = 1000;
    private static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final RefreshTokenHistoryRepository repository;
    private final ClusterJobLock jobLock;

    public RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository) {
        this(repository, (ClusterJobLock) null);
    }

    @Autowired
    public RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(repository, jobLock.getIfAvailable());
    }

    RefreshTokenHistoryRetentionJob(RefreshTokenHistoryRepository repository, ClusterJobLock jobLock) {
        this.repository = repository;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.auth.refresh-token-history.retention-cron:0 20 3 * * *}")
    public void deleteExpiredHistory() {
        // One instance runs the purge, like the other retention jobs (P1-7); the table is RLS-protected and a
        // scheduler thread has no request tenant, hence the maintenance scope.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            if (jobLock == null) {
                purge();
            } else {
                jobLock.runExclusively(JOB_NAME, this::purge);
            }
        }
    }

    private void purge() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        // Batch by batch until a partial batch signals the tail, or the budget is spent (the next run resumes).
        while (repository.deleteConsumedBefore(cutoff, BATCH_SIZE) >= BATCH_SIZE && System.nanoTime() - deadline < 0) {
            // next batch
        }
    }
}
