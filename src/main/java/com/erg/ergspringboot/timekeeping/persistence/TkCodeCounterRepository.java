package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkCodeCounterRepository extends JpaRepository<TkCodeCounter, String> {

    /** Atomic upsert-increment: concurrent callers serialise on the (tenant, kind, scope) row. Returns the new value. */
    @Query(value = """
            insert into tk_code_counters (id, tenant_id, kind, scope, last_value, created_at, updated_at)
            values (:id, :tenantId, :kind, :scope, 1, now(), now())
            on conflict (tenant_id, kind, scope)
            do update set last_value = tk_code_counters.last_value + 1, updated_at = now()
            returning last_value
            """, nativeQuery = true)
    long increment(@Param("id") String id, @Param("tenantId") String tenantId, @Param("kind") String kind,
            @Param("scope") String scope);
}
