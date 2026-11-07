package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkRequestStepRepository extends JpaRepository<TkRequestStep, String> {

    List<TkRequestStep> findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(String tenantId, String requestId);

    List<TkRequestStep> findByTenantIdAndRequestIdInOrderByStepNoAscApproverIdAsc(String tenantId,
            Collection<String> requestIds);

    boolean existsByTenantIdAndRequestIdAndApproverId(String tenantId, String requestId, String approverId);

    boolean existsByTenantIdAndRequestIdAndActedBy(String tenantId, String requestId, String actedBy);

    /** WAITING rows of the current step of every PENDING request of the schools (today dashboard). */
    @Query("""
            select s from TkRequestStep s, TkRequest r
            where r.tenantId = :tenantId and s.tenantId = r.tenantId and s.requestId = r.id
              and r.status = com.erg.ergspringboot.timekeeping.RequestStatus.PENDING and r.schoolId in :schoolIds
              and s.stepNo = r.currentStep and s.status = com.erg.ergspringboot.timekeeping.StepStatus.WAITING
            """)
    List<TkRequestStep> findCurrentWaitingSteps(@Param("tenantId") String tenantId,
            @Param("schoolIds") Collection<String> schoolIds);
}
