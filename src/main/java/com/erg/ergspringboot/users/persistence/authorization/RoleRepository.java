package com.erg.ergspringboot.users.persistence.authorization;

import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.projection.RoleOptionProjection;
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
            select new com.erg.ergspringboot.users.persistence.authorization.projection.RoleOptionProjection(r.id, r.name)
            from Role r
            order by lower(r.name) asc, r.id asc
            """)
    List<RoleOptionProjection> findAccessManagementRoleOptions();

    @Query("""
            select new com.erg.ergspringboot.users.persistence.authorization.projection.RoleOptionProjection(r.id, r.name)
            from Role r
            order by lower(r.name) asc, r.id asc
            """)
    List<RoleOptionProjection> findAccessManagementRoleOptions(Pageable pageable);

    // Fetch permissions only; users load lazily and are batched via hibernate.default_batch_fetch_size.
    // Fetching both collections in one query is a cartesian product (P permissions x U users per role).
    @Query("select distinct role from Role role left join fetch role.permissions")
    List<Role> findAllWithPermissionsAndUsers();

    /**
     * [roleId, distinct users] per role in one grouped query: users holding the role directly plus users with an active
     * scoped assignment (a user in both counts once). Replaces loading every User through {@code Role.getUsers()}.
     */
    @Query(value = """
            select x.role_id, count(distinct x.user_id) from (
                select ur.role_id, ur.user_id from user_roles ur join users u on u.id = ur.user_id
                union
                select a.role_id, a.user_id from user_role_assignments a
                where a.revoked_at is null and (a.expires_at is null or a.expires_at > :now)
            ) x group by x.role_id
            """, nativeQuery = true)
    List<Object[]> countUsersByRole(@Param("now") java.time.OffsetDateTime now);

    /**
     * [permissionId, distinct users] per permission in one grouped query: direct holders of any role carrying it plus users
     * with a per-user override on it. Replaces loading every User through {@code Role.getUsers()} (same scope: all tenants).
     */
    @Query(value = """
            select x.permission_id, count(distinct x.user_id) from (
                select rp.permission_id, ur.user_id from role_permissions rp
                join user_roles ur on ur.role_id = rp.role_id where rp.permission_id in (:ids)
                union
                select p.id, o.user_id from user_permissions o join permissions p on p.name = o.permission
                where p.id in (:ids)
            ) x group by x.permission_id
            """, nativeQuery = true)
    List<Object[]> countUsersByPermission(@Param("ids") Collection<String> permissionIds);

    @Query("""
            select distinct role from Role role
            join fetch role.permissions permission
            where permission.id in :permissionIds
            """)
    List<Role> findAllWithPermissionsAndUsersByPermissionIds(@Param("permissionIds") Collection<String> permissionIds);
}
