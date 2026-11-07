package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkRequestEvidenceRepository extends JpaRepository<TkRequestEvidence, String> {

    List<TkRequestEvidence> findByTenantIdAndRequestIdInOrderByRefAsc(String tenantId, Collection<String> requestIds);
}
