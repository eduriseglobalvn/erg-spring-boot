package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmSchoolActivity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolActivityRepository extends JpaRepository<CrmSchoolActivity, String> {

    Optional<CrmSchoolActivity> findByIdAndTenantId(String id, String tenantId);

    List<CrmSchoolActivity> findByTenantIdAndSchoolIdOrderByCreatedAtDesc(String tenantId, String schoolId);

    List<CrmSchoolActivity> findByTenantIdAndSchoolIdInOrderByDueAtAsc(String tenantId, Collection<String> schoolIds);

    @org.springframework.data.jpa.repository.Query("""
            select a from CrmSchoolActivity a
            where a.tenantId = :tenantId and a.schoolId in :schoolIds and (a.done is null or a.done = false)
            order by a.dueAt asc
            """)
    List<CrmSchoolActivity> findOpenFollowUps(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("schoolIds") Collection<String> schoolIds);

    /** Open follow-ups plus completed ones due on or after {@code completedSince} (undated ones are kept). */
    @org.springframework.data.jpa.repository.Query("""
            select a from CrmSchoolActivity a
            where a.tenantId = :tenantId and a.schoolId in :schoolIds
              and (a.done is null or a.done = false or a.dueAt is null or a.dueAt >= :completedSince)
            order by a.dueAt asc
            """)
    List<CrmSchoolActivity> findFollowUpsWithRecentCompleted(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("schoolIds") Collection<String> schoolIds,
            @org.springframework.data.repository.query.Param("completedSince") java.time.LocalDate completedSince);

    List<CrmSchoolActivity> findByTenantIdAndSchoolIdAndIdIn(String tenantId, String schoolId, Collection<String> ids);

    Optional<CrmSchoolActivity> findByTenantIdAndActorUserIdAndIdempotencyKey(
            String tenantId, String actorUserId, String idempotencyKey);
}
