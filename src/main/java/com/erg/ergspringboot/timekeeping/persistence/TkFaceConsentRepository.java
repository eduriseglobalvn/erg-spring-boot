package com.erg.ergspringboot.timekeeping.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TkFaceConsentRepository extends JpaRepository<TkFaceConsent, String> {

    boolean existsByTenantIdAndUserId(String tenantId, String userId);

    /** Race-safe idempotent consent: the unique (tenant_id, user_id) row absorbs repeats. */
    @Modifying
    @Transactional
    @Query(value = """
            insert into tk_face_consents (id, tenant_id, user_id, consented_at)
            values (:id, :tenantId, :userId, :consentedAt)
            on conflict (tenant_id, user_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("tenantId") String tenantId,
            @Param("userId") String userId, @Param("consentedAt") Instant consentedAt);
}
