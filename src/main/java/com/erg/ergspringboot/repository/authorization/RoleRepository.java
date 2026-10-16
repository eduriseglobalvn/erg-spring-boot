package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface RoleRepository extends JpaRepository<Role, String> {

    List<Role> findByNameInIgnoreCase(Collection<String> names);

    Optional<Role> findByNameIgnoreCase(String name);

    @Query("""
            select new com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection(r.id, r.name)
            from Role r
            order by lower(r.name) asc, r.id asc
            """)
    List<RoleOptionProjection> findAccessManagementRoleOptions();

    @Query("""
            select new com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection(r.id, r.name)
            from Role r
            order by lower(r.name) asc, r.id asc
            """)
    List<RoleOptionProjection> findAccessManagementRoleOptions(Pageable pageable);

    // Fetch permissions only; users load lazily and are batched via hibernate.default_batch_fetch_size.
    // Fetching both collections in one query is a cartesian product (P permissions x U users per role).
    @Query("select distinct role from Role role left join fetch role.permissions")
    List<Role> findAllWithPermissionsAndUsers();

    @Query("""
            select distinct role from Role role
            join fetch role.permissions permission
            where permission.id in :permissionIds
            """)
    List<Role> findAllWithPermissionsAndUsersByPermissionIds(@Param("permissionIds") Collection<String> permissionIds);
}
