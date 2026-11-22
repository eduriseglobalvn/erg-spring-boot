package com.erg.ergspringboot.lcms.persistence;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserAccessRowProjection;
import com.erg.ergspringboot.users.persistence.organization.projection.ScopeOptionProjection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read queries of the access-management workspace that the users repositories cannot serve cheaply at tenant scale. */
public interface LcmsAccessReadRepository extends Repository<User, String> {

    /**
     * Same rows and counts as {@code UserRepository.findAccessManagementRows}, but the page is cut first and the role and
     * scope counts are correlated subselects, so a tenant with tens of thousands of users no longer groups them all.
     */
    @Query(
            value = """
                    select new com.erg.ergspringboot.users.persistence.UserAccessRowProjection(
                        u.id,
                        u.fullName,
                        u.email,
                        u.status,
                        (select count(distinct r.id) from User ru join ru.roles r where ru.id = u.id),
                        (select count(distinct s.id) from User su join su.accessScopes s where su.id = u.id),
                        u.createdAt
                    )
                    from User u
                    where u.tenantId = :tenantId
                    and (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.fullName, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%')))
                    and ((:status is null and lower(coalesce(u.status, '')) <> 'deleted')
                        or lower(u.status) = lower(:status))
                    and (:roleId is null or exists (
                        select 1 from u.roles roleFilter
                        where roleFilter.id = :roleId
                    ))
                    order by u.createdAt desc, u.id asc
                    """,
            countQuery = """
                    select count(u.id)
                    from User u
                    where u.tenantId = :tenantId
                    and (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.fullName, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%')))
                    and ((:status is null and lower(coalesce(u.status, '')) <> 'deleted')
                        or lower(u.status) = lower(:status))
                    and (:roleId is null or exists (
                        select 1 from u.roles roleFilter
                        where roleFilter.id = :roleId
                    ))
                    """
    )
    Page<UserAccessRowProjection> findRows(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("roleId") String roleId,
            @Param("status") String status,
            Pageable pageable
    );

    /**
     * The education units some user of the tenant is scoped to (what the old query found by scanning every scope row):
     * driven from the few units, with an index probe on {@code user_access_scopes.education_unit_id} each.
     */
    @Query("""
            select new com.erg.ergspringboot.users.persistence.organization.projection.ScopeOptionProjection(e.id, e.name, e.type)
            from EducationUnit e
            where e.tenantId = :tenantId
            and exists (select 1 from UserAccessScope s join s.user u
                        where s.educationUnitId = e.id and u.tenantId = :tenantId)
            order by lower(e.name) asc, e.id asc
            """)
    List<ScopeOptionProjection> findScopeOptions(@Param("tenantId") String tenantId, Pageable pageable);
}
