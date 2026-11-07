package com.erg.ergspringboot.timekeeping.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TkPunchRepository extends JpaRepository<TkPunch, String> {

    interface PhotoPurgeRef {
        String getId();

        String getTenantId();

        String getPhotoKey();
    }

    Optional<TkPunch> findByIdAndTenantId(String id, String tenantId);

    Optional<TkPunch> findByTenantIdAndUserIdAndIdempotencyKey(String tenantId, String userId, String idempotencyKey);

    List<TkPunch> findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(
            String tenantId, String userId, LocalDate workDate);

    List<TkPunch> findByTenantIdAndWorkDateOrderByServerTimeAscCodeAsc(String tenantId, LocalDate workDate);

    List<TkPunch> findByTenantIdAndUserIdAndWorkDateBetweenOrderByServerTimeAscCodeAsc(
            String tenantId, String userId, LocalDate from, LocalDate to);

    /** People of one shift and day who have a non-rejected IN but no non-rejected OUT yet. */
    @Query(value = """
            select distinct p.user_id from tk_punches p
            where p.tenant_id = :tenantId and p.shift_id = :shiftId and p.work_date = :workDate
              and p.direction = 'IN' and p.status <> 'REJECTED'
              and not exists (
                  select 1 from tk_punches o
                  where o.tenant_id = p.tenant_id and o.user_id = p.user_id and o.work_date = p.work_date
                    and o.direction = 'OUT' and o.status <> 'REJECTED')
            order by p.user_id
            """, nativeQuery = true)
    List<String> findUsersInWithoutOut(@Param("tenantId") String tenantId, @Param("shiftId") String shiftId,
            @Param("workDate") LocalDate workDate);

    /** Punches holding a photo taken before {@code cutoff}, keyset-paged by id so a backlog loads in bounded batches. */
    @Query(value = """
            select id as id, tenant_id as tenantId, photo_key as photoKey
            from tk_punches
            where photo_key is not null and photo_purged_at is null
              and server_time < :cutoff
              and id > :afterId
            order by id
            limit :limit
            """, nativeQuery = true)
    List<PhotoPurgeRef> findPhotoPurgeCandidates(
            @Param("cutoff") Instant cutoff, @Param("afterId") String afterId, @Param("limit") int limit);

    /** Clears the key only if it is still the one whose object was deleted; returns 0 otherwise. */
    @Modifying
    @Transactional
    @Query("""
            update TkPunch p set p.photoKey = null, p.photoPurgedAt = :now
            where p.id = :id and p.tenantId = :tenantId and p.photoKey = :photoKey
            """)
    int markPhotoPurged(@Param("id") String id, @Param("tenantId") String tenantId,
            @Param("photoKey") String photoKey, @Param("now") Instant now);
}
