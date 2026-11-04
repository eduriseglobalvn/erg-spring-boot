package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.persistence.AttemptExpiryRepository;
import com.erg.ergspringboot.lms.persistence.AttemptExpiryRepository.ExpiredAttemptRef;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P2-5: closes in-progress attempts whose deadline plus grace passed even when the student never comes
 * back, so teacher reports stop showing them as running. Each attempt closes in its own transaction.
 */
@Component
public class AttemptExpirySweepJob {
    static final String JOB_NAME = "lms-attempt-expiry-sweep";
    private static final Logger log = LoggerFactory.getLogger(AttemptExpirySweepJob.class);

    private final AttemptExpiryRepository candidates;
    private final LmsAttemptUseCase attempts;
    private final boolean enabled;
    private final int batchSize;
    private final ClusterJobLock jobLock;

    @Autowired
    public AttemptExpirySweepJob(
            AttemptExpiryRepository candidates,
            LmsAttemptUseCase attempts,
            @Value("${erg.lms.attempt-expiry-sweep.enabled:true}") boolean enabled,
            @Value("${erg.lms.attempt-expiry-sweep.batch-size:200}") int batchSize,
            ObjectProvider<ClusterJobLock> jobLock) {
        this(candidates, attempts, enabled, batchSize, jobLock.getIfAvailable());
    }

    AttemptExpirySweepJob(AttemptExpiryRepository candidates, LmsAttemptUseCase attempts, boolean enabled,
            int batchSize, ClusterJobLock jobLock) {
        this.candidates = candidates;
        this.attempts = attempts;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.jobLock = jobLock;
    }

    @Scheduled(fixedDelayString = "${erg.lms.attempt-expiry-sweep.fixed-delay-ms:300000}")
    public void sweep() {
        if (!enabled) return;
        if (jobLock == null) {
            closeExpired();
        } else {
            jobLock.runExclusively(JOB_NAME, this::closeExpired);
        }
    }

    int closeExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ExpiredAttemptRef> refs;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            refs = candidates.findExpiredCandidates(now, batchSize);
        }
        int closed = 0;
        for (ExpiredAttemptRef ref : refs) {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(ref.getTenantId(), null, "system"))) {
                if (attempts.closeIfExpired(ref.getTenantId(), ref.getId(), now)) closed++;
            } catch (RuntimeException ex) {
                // A concurrent submit or a missing assignment must not stop the rest of the batch.
                log.warn("Expired-attempt sweep skipped attempt {}: {}", ref.getId(), ex.getMessage());
            }
        }
        if (closed > 0) log.info("EXAM_ATTEMPT_EXPIRY_SWEEP closed={} candidates={}", closed, refs.size());
        return closed;
    }
}
