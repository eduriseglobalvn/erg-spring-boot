package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.PermissionGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, String> {

    List<PermissionGroup> findAllByOrderByOrderAscNameAsc();
}
