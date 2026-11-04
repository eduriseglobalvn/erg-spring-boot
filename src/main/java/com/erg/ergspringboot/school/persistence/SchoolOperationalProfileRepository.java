package com.erg.ergspringboot.school.persistence;

import com.erg.ergspringboot.school.persistence.SchoolOperationalProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolOperationalProfileRepository extends JpaRepository<SchoolOperationalProfile, String> {
    long countByTenantId(String tenantId);

    Optional<SchoolOperationalProfile> findBySchoolIdAndTenantId(String schoolId, String tenantId);

    List<SchoolOperationalProfile> findByTenantIdOrderBySchoolIdAsc(String tenantId);
}
