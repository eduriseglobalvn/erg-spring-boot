package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, String> {

    void deleteByUserId(String userId);

    List<UserPermissionOverride> findByUserId(String userId);
}
