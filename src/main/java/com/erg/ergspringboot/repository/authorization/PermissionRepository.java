package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findByNameInIgnoreCase(Collection<String> names);

    List<Permission> findByGroupNameOrderByNameAsc(String groupName);

    List<Permission> findByGroupNameInOrderByGroupNameAscNameAsc(Collection<String> groupNames);

    /** Hard upper bound for the legacy bootstrap/catalog endpoint. */
    List<Permission> findTop500ByOrderByNameAsc();

    @Query(
            value = """
                    select p from Permission p
                    where (cast(:search as string) is null
                        or lower(p.name) like lower(concat('%', cast(:search as string), '%'))
                        or lower(coalesce(p.label, '')) like lower(concat('%', cast(:search as string), '%'))
                        or lower(coalesce(p.description, '')) like lower(concat('%', cast(:search as string), '%')))
                    and (cast(:groupName as string) is null or lower(p.groupName) = lower(cast(:groupName as string)))
                    and (cast(:portal as string) is null or lower(p.portal) = lower(cast(:portal as string)))
                    and (cast(:status as string) is null or lower(p.status) = lower(cast(:status as string)))
                    and (:used is null
                        or (:used = true and (
                            exists (select binding.id from PermissionBinding binding
                                where binding.permission = p and binding.enforced = true)
                            or exists (select role.id from Role role join role.permissions rolePermission
                                where rolePermission = p)
                            or exists (select override.id from UserPermissionOverride override
                                where override.permissionRef = p)
                        ))
                        or (:used = false
                            and not exists (select binding.id from PermissionBinding binding
                                where binding.permission = p and binding.enforced = true)
                            and not exists (select role.id from Role role join role.permissions rolePermission
                                where rolePermission = p)
                            and not exists (select override.id from UserPermissionOverride override
                                where override.permissionRef = p)))
                    """,
            countQuery = """
                    select count(p.id) from Permission p
                    where (cast(:search as string) is null
                        or lower(p.name) like lower(concat('%', cast(:search as string), '%'))
                        or lower(coalesce(p.label, '')) like lower(concat('%', cast(:search as string), '%'))
                        or lower(coalesce(p.description, '')) like lower(concat('%', cast(:search as string), '%')))
                    and (cast(:groupName as string) is null or lower(p.groupName) = lower(cast(:groupName as string)))
                    and (cast(:portal as string) is null or lower(p.portal) = lower(cast(:portal as string)))
                    and (cast(:status as string) is null or lower(p.status) = lower(cast(:status as string)))
                    and (:used is null
                        or (:used = true and (
                            exists (select binding.id from PermissionBinding binding
                                where binding.permission = p and binding.enforced = true)
                            or exists (select role.id from Role role join role.permissions rolePermission
                                where rolePermission = p)
                            or exists (select override.id from UserPermissionOverride override
                                where override.permissionRef = p)
                        ))
                        or (:used = false
                            and not exists (select binding.id from PermissionBinding binding
                                where binding.permission = p and binding.enforced = true)
                            and not exists (select role.id from Role role join role.permissions rolePermission
                                where rolePermission = p)
                            and not exists (select override.id from UserPermissionOverride override
                                where override.permissionRef = p)))
                    """
    )
    Page<Permission> searchCatalog(
            @Param("search") String search,
            @Param("groupName") String groupName,
            @Param("portal") String portal,
            @Param("status") String status,
            @Param("used") Boolean used,
            Pageable pageable
    );
}
