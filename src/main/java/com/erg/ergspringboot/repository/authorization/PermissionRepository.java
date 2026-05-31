package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findByNameInIgnoreCase(Collection<String> names);

    List<Permission> findByGroupNameOrderByNameAsc(String groupName);
}
