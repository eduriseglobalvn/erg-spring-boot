package com.erg.ergspringboot.repository.users;

import com.erg.ergspringboot.model.users.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserActivityRepository extends JpaRepository<UserActivity, String> {

    Page<UserActivity> findByTargetUserIdAndTenantIdOrderByCreatedAtDescIdDesc(
            String targetUserId, String tenantId, Pageable pageable);

    Page<UserActivity> findByTenantIdOrderByCreatedAtDescIdDesc(String tenantId, Pageable pageable);

    Page<UserActivity> findByTenantIdAndTargetUserIdOrderByCreatedAtDescIdDesc(
            String tenantId, String targetUserId, Pageable pageable);
}
