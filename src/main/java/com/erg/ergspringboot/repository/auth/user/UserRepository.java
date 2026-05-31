package com.erg.ergspringboot.repository.auth.user;

import com.erg.ergspringboot.model.users.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndTenantId(String id, String tenantId);
}
