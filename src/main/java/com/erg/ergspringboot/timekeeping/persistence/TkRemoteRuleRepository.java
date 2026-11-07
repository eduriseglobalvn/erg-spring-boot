package com.erg.ergspringboot.timekeeping.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkRemoteRuleRepository extends JpaRepository<TkRemoteRule, String> {

    List<TkRemoteRule> findByTenantIdAndSchoolIdAndActiveTrue(String tenantId, String schoolId);
}
