package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded retention worker; no unbounded scan or executor is created.
 *
 * <p>{@code retention_until} is already an absolute deadline (occurredAt + retention days,
 * or a caller-supplied shorter value), so expired rows are those with
 * {@code retention_until < now()}; subtracting the retention window again would keep rows
 * twice as long (N3). One instance runs the purge at a time (cluster advisory lock).</p>
 */
@Service
@ConditionalOnProperty(name = "erg.audit.retention.enabled", havingValue = "true", matchIfMissing = true)
public class AuditRetentionService {
    static final String JOB_NAME = "audit-retention";

    private final AuditEventRepository repository;
    private final EntityManager entityManager;
    private final int batchSize;
    private final ClusterJobLock jobLock;
    private final AtomicBoolean running = new AtomicBoolean();

    public AuditRetentionService(
            AuditEventRepository repository, EntityManager entityManager, int batchSize, int retentionDays) {
        this(repository, entityManager, batchSize, retentionDays, (ClusterJobLock) null);
    }

    @Autowired
    public AuditRetentionService(
            AuditEventRepository repository,
            EntityManager entityManager,
            @Value("${erg.audit.retention.batch-size:500}") int batchSize,
            @Value("${erg.audit.retention.default-days:365}") int retentionDays,
            ObjectProvider<ClusterJobLock> jobLock
    ) {
        this(repository, entityManager, batchSize, retentionDays, jobLock.getIfAvailable());
    }

    AuditRetentionService(
            AuditEventRepository repository, EntityManager entityManager, int batchSize, int retentionDays,
            ClusterJobLock jobLock) {
        if (batchSize < 1 || batchSize > 5000 || retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("Invalid audit retention bounds");
        }
        this.repository = repository;
        this.entityManager = entityManager;
        this.batchSize = batchSize;
        this.jobLock = jobLock;
    }

    @Scheduled(fixedDelayString = "${erg.audit.retention.fixed-delay:3600000}")
    @Transactional("postgresTransactionManager")
    public void runScheduled() {
        if (jobLock == null) {
            purge(OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            jobLock.runExclusively(JOB_NAME, () -> purge(OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }

    @Transactional("postgresTransactionManager")
    public int purge(OffsetDateTime cutoff) {
        if (!running.compareAndSet(false, true)) return 0;
        try {
            // RLS is intentionally opened only for this bounded, database-owned path.
            entityManager.createNativeQuery("select set_config('app.maintenance_mode','true',true)")
                    .getSingleResult();
            entityManager.createNativeQuery("select set_config('app.audit_retention_job','true',true)")
                    .getSingleResult();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            // Never purge rows whose absolute deadline is still in the future.
            OffsetDateTime effective = cutoff == null || cutoff.isAfter(now) ? now : cutoff;
            return repository.deleteExpired(effective, batchSize);
        } finally {
            running.set(false);
        }
    }
}
