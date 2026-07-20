package com.erg.ergspringboot.repository.reporting;

import com.erg.ergspringboot.model.reporting.ReportJob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportJobRepository extends JpaRepository<ReportJob, String> {
    Optional<ReportJob> findByIdAndTenantId(String id, String tenantId);
    Optional<ReportJob> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query(value = """
            select * from report_jobs
            where (status = 'queued' and available_at <= :now)
               or (status = 'processing' and lease_until < :now)
            order by available_at asc, created_at asc
            for update skip locked
            limit :limit
            """, nativeQuery = true)
    List<ReportJob> findDueForUpdate(@Param("now") OffsetDateTime now, @Param("limit") int limit);
}
