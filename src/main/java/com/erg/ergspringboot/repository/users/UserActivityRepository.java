package com.erg.ergspringboot.repository.users;

import com.erg.ergspringboot.model.users.UserActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityRepository extends JpaRepository<UserActivity, String> {

    List<UserActivity> findByTargetUserIdAndTenantIdOrderByCreatedAtDesc(String targetUserId, String tenantId);
}
