package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmReferral;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmReferralRepository extends JpaRepository<CrmReferral, String> {

    List<CrmReferral> findByTenantIdAndSchoolIdOrderByCreatedAtAsc(String tenantId, String schoolId);
}
