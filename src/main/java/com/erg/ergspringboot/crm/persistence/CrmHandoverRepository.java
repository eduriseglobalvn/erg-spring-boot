package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmHandover;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmHandoverRepository extends JpaRepository<CrmHandover, String> {

    List<CrmHandover> findByTenantIdAndSchoolIdInOrderByCreatedAtDesc(String tenantId, Collection<String> schoolIds);

    Optional<CrmHandover> findByTenantIdAndRequestedByAndIdempotencyKey(
            String tenantId, String requestedBy, String idempotencyKey);

    Optional<CrmHandover> findByIdAndTenantId(String id, String tenantId);
}
