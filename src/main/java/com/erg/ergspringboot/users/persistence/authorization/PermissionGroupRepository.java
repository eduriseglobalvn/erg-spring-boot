package com.erg.ergspringboot.users.persistence.authorization;

import com.erg.ergspringboot.users.persistence.authorization.PermissionGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, String> {

    List<PermissionGroup> findAllByOrderByOrderAscNameAsc();
}
