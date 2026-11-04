package com.erg.ergspringboot.crm.persistence;

import com.erg.ergspringboot.crm.persistence.CrmSchoolProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmSchoolProfileRepository extends JpaRepository<CrmSchoolProfile, String> {

    Optional<CrmSchoolProfile> findByIdAndTenantId(String id, String tenantId);

    Optional<CrmSchoolProfile> findFirstByTenantIdAndSchoolId(String tenantId, String schoolId);

    List<CrmSchoolProfile> findByTenantIdAndSchoolIdIn(String tenantId, Collection<String> schoolIds);
}
