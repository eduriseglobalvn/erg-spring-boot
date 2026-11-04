package com.erg.ergspringboot.audit.persistence;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.persistence.AuditEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    Optional<AuditEvent> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<AuditEvent> findTopByTenantIdOrderByOccurredAtDescIdDesc(String tenantId);

    @Query("""
            select e from AuditEvent e
            where e.tenantId = :tenantId
              and (:from is null or e.occurredAt >= :from)
              and (:to is null or e.occurredAt <= :to)
              and (:actorId is null or e.actorId = :actorId)
              and (:resourceType is null or e.resourceType = :resourceType)
              and (:resourceId is null or e.resourceId = :resourceId)
              and (:outcome is null or e.outcome = :outcome)
              and (:allScopes = true
                   or e.scopeType is null
                   or e.scopeType = 'TENANT'
                   or (e.scopeType = 'EDUCATION_UNIT' and e.scopeId in :educationUnitIds)
                   or (e.scopeType = 'CLASS' and e.scopeId in :classIds))
            order by e.occurredAt desc, e.id desc
            """)
    Page<AuditEvent> search(
            @Param("tenantId") String tenantId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("actorId") String actorId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("outcome") AuditOutcome outcome,
            @Param("allScopes") boolean allScopes,
            @Param("educationUnitIds") List<String> educationUnitIds,
            @Param("classIds") List<String> classIds,
            Pageable pageable);

    @Modifying
    @Query(value = """
            delete from audit_events
             where ctid in (
                 select ctid from audit_events
                  where retention_until < :cutoff
                    and legal_hold = false
                  order by retention_until asc, id asc
                  limit :limit
             )
            """, nativeQuery = true)
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);

    /** The only permitted mutation (V121 trigger): toggling legal_hold within the caller's tenant. */
    @Modifying
    @Query(value = """
            update audit_events
               set legal_hold = :legalHold, updated_at = now()
             where tenant_id = :tenantId and id = :id
            """, nativeQuery = true)
    int updateLegalHold(@Param("tenantId") String tenantId, @Param("id") String id,
            @Param("legalHold") boolean legalHold);
}
