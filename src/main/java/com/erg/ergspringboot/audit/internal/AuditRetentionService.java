package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded retention worker; no unbounded scan or executor is created. */
@Service
@ConditionalOnProperty(name = "erg.audit.retention.enabled", havingValue = "true", matchIfMissing = true)
public class AuditRetentionService {
    private final AuditEventRepository repository;
    private final EntityManager entityManager;
    private final int batchSize;
    private final int retentionDays;
    private final AtomicBoolean running = new AtomicBoolean();

    public AuditRetentionService(
            AuditEventRepository repository,
            EntityManager entityManager,
            @Value("${erg.audit.retention.batch-size:500}") int batchSize,
            @Value("${erg.audit.retention.default-days:365}") int retentionDays
    ) {
        if (batchSize < 1 || batchSize > 5000 || retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("Invalid audit retention bounds");
        }
        this.repository = repository;
        this.entityManager = entityManager;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${erg.audit.retention.fixed-delay:3600000}")
    @Transactional("postgresTransactionManager")
    public void runScheduled() {
        purge(OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays));
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
            return repository.deleteExpired(cutoff == null
                    ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays) : cutoff, batchSize);
        } finally {
            running.set(false);
        }
    }
}
