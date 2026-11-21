package com.erg.ergspringboot.enterpriseconnectors.persistence;

import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnterpriseConnectorRunRepository extends JpaRepository<EnterpriseConnectorRun, String> {
    Optional<EnterpriseConnectorRun> findByTenantIdAndId(String tenantId, String id);

    List<EnterpriseConnectorRun> findTop100ByTenantIdAndConnectorIdOrderByCreatedAtDesc(
            String tenantId, String connectorId);

    /** A RUNNING row older than the cutoff lost its worker (JVM died mid provider call); it becomes retryable. */
    @Modifying
    @Query("""
            update EnterpriseConnectorRun r
            set r.status = 'RETRYABLE_FAILED', r.errorCode = 'RUN_TIMED_OUT', r.updatedAt = :now,
                r.recordVersion = r.recordVersion + 1
            where r.status = 'RUNNING' and r.updatedAt < :cutoff
            """)
    int failStuckRuns(@Param("cutoff") OffsetDateTime cutoff, @Param("now") OffsetDateTime now);
}
