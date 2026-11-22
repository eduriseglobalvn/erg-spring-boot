package com.erg.ergspringboot.auth.internal.session;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
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
 * Purges dead {@code user_sessions} (expired or revoked) and old {@code auth_login_attempts}. Lockout reads only the
 * last 15 minutes and the security feeds are paged recent history, so the default 90 days is far beyond any reader.
 */
@Component
public class AuthDataRetentionJob {
    static final String JOB_NAME = "auth-data-retention";
    private static final int BATCH_SIZE = 1000;
    private static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final AuthSessionRepository sessions;
    private final AuthLoginAttemptRepository attempts;
    private final ClusterJobLock jobLock;
    private final int retentionDays;

    @Autowired
    public AuthDataRetentionJob(AuthSessionRepository sessions, AuthLoginAttemptRepository attempts,
            ObjectProvider<ClusterJobLock> jobLock,
            @Value("${erg.auth.data-retention.days:90}") int retentionDays) {
        this(sessions, attempts, jobLock.getIfAvailable(), retentionDays);
    }

    AuthDataRetentionJob(AuthSessionRepository sessions, AuthLoginAttemptRepository attempts,
            ClusterJobLock jobLock, int retentionDays) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.jobLock = jobLock;
        this.retentionDays = Math.max(30, retentionDays);
    }

    @Scheduled(cron = "${erg.auth.data-retention.cron:0 50 3 * * *}")
    public void purgeOldAuthData() {
        // Tables are RLS-protected and a scheduler thread has no request tenant, hence the maintenance scope.
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
        long deadline = System.nanoTime() + RUN_BUDGET.toNanos();
        drain(() -> sessions.deleteDeadBefore(cutoff, BATCH_SIZE), deadline);
        drain(() -> attempts.deleteOlderThan(cutoff, BATCH_SIZE), deadline);
    }

    /** Batch by batch until a partial batch signals the tail, or the budget is spent (the next run resumes). */
    private static void drain(IntSupplier batch, long deadline) {
        while (batch.getAsInt() >= BATCH_SIZE && System.nanoTime() - deadline < 0) {
            // next batch
        }
    }
}
