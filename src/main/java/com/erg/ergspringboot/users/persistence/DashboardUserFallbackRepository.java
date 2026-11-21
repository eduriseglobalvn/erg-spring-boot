package com.erg.ergspringboot.users.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Name, email and status of a login, for the dashboard profile fallback; unlike the entity graph it reads no roles or permissions. */
public interface DashboardUserFallbackRepository extends Repository<User, String> {

    @Query("select u.fullName as fullName, u.email as email, u.status as status from User u where u.tenantId = :tenantId and u.id = :id")
    Optional<UserFallback> findFallback(String tenantId, String id);

    interface UserFallback {
        String getFullName();

        String getEmail();

        String getStatus();
    }
}
