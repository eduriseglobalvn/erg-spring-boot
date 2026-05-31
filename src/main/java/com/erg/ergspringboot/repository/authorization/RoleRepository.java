package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    List<Role> findByNameInIgnoreCase(Collection<String> names);

    Optional<Role> findByNameIgnoreCase(String name);
}
