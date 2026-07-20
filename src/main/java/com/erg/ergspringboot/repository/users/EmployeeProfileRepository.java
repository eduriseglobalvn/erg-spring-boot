package com.erg.ergspringboot.repository.users;

import com.erg.ergspringboot.model.users.EmployeeProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, String> {
    Optional<EmployeeProfile> findByUserIdAndTenantId(String userId, String tenantId);

    List<EmployeeProfile> findByTenantIdAndUserIdIn(String tenantId, Collection<String> userIds);

    boolean existsByTenantIdAndEmployeeCodeIgnoreCase(String tenantId, String employeeCode);

    boolean existsByTenantIdAndEmployeeCodeIgnoreCaseAndUserIdNot(String tenantId, String employeeCode, String userId);
}
