package com.erg.ergspringboot.timekeeping.persistence;

import java.time.Instant;
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
