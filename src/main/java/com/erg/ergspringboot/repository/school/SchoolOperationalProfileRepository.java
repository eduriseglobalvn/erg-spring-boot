package com.erg.ergspringboot.repository.school;

import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolOperationalProfileRepository extends JpaRepository<SchoolOperationalProfile, String> {
    long countByTenantId(String tenantId);

    Optional<SchoolOperationalProfile> findBySchoolIdAndTenantId(String schoolId, String tenantId);

    List<SchoolOperationalProfile> findByTenantIdOrderBySchoolIdAsc(String tenantId);
}
