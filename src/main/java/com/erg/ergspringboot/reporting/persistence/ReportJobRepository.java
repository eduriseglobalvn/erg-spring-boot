package com.erg.ergspringboot.reporting.persistence;

import com.erg.ergspringboot.reporting.persistence.ReportJob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportJobRepository extends JpaRepository<ReportJob, String> {

    /** Everything a status poll needs except {@code result_json}, which can hold up to 10k rows. */
    interface Status {
        String getId();

        String getReportType();

        String getAggregateId();

        String getStatus();

        int getProgress();

        OffsetDateTime getCreatedAt();

        OffsetDateTime getCompletedAt();

        String getLastError();

        String getFormat();

        OffsetDateTime getExpiresAt();

        String getRequestedBy();
    }

    Optional<Status> findStatusByIdAndTenantId(String id, String tenantId);
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

    /** Bounded retention purge: finished jobs whose download window closed before the cutoff. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = """
            delete from report_jobs
            where id in (
                select id from report_jobs
                where expires_at < :cutoff and status not in ('queued', 'processing')
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);
}
