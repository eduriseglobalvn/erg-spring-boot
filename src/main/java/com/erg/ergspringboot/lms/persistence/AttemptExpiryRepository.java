package com.erg.ergspringboot.lms.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Candidates for the expired-attempt sweep (P2-5); kept apart so AttemptRepository does not grow. */
public interface AttemptExpiryRepository extends Repository<Attempt, String> {

    interface ExpiredAttemptRef {
        String getTenantId();

        String getId();
    }

    /**
     * In-progress attempts whose live assignment deadline (due date or start + duration) is before
     * {@code cutoff}. The caller re-checks the frozen snapshot deadline and the grace before closing.
     */
    // shortcut: uses the live assignment deadline as the coarse filter, so an attempt whose snapshot deadline is
    // later than the live one is re-checked each run; add a snapshot deadline column if that set ever grows.
    @Query(value = """
            select a.tenant_id as tenantId, a.id as id
            from lms_attempts a
            join lms_assignments s on s.tenant_id = a.tenant_id and s.id = a.assignment_id
            where lower(a.status) = 'in_progress'
              and ((s.due_at is not null and s.due_at < :cutoff)
                or (s.max_duration_minutes > 0 and a.started_at is not null
                    and a.started_at + make_interval(mins => s.max_duration_minutes) < :cutoff))
            order by a.started_at
            limit :limit
            """, nativeQuery = true)
    List<ExpiredAttemptRef> findExpiredCandidates(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);
}
