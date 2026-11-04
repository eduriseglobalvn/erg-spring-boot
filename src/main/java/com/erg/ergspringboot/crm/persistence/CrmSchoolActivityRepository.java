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

    List<CrmSchoolActivity> findByTenantIdAndSchoolIdAndIdIn(String tenantId, String schoolId, Collection<String> ids);

    Optional<CrmSchoolActivity> findByTenantIdAndActorUserIdAndIdempotencyKey(
            String tenantId, String actorUserId, String idempotencyKey);
}
