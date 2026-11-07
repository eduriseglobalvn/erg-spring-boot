package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkRequestStepRepository extends JpaRepository<TkRequestStep, String> {

    List<TkRequestStep> findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(String tenantId, String requestId);

    List<TkRequestStep> findByTenantIdAndRequestIdInOrderByStepNoAscApproverIdAsc(String tenantId,
            Collection<String> requestIds);
}
