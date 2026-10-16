package com.erg.ergspringboot.repository.crm;

import com.erg.ergspringboot.model.crm.CrmHandover;
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
