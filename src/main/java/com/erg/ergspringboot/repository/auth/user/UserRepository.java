package com.erg.ergspringboot.repository.auth.user;

import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.projection.UserAccessRowProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserListItemProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserRoleNameProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "permissionOverrides", "accessScopes"})
    @Query("""
            select u from User u
            where u.tenantId = :tenantId
            and (lower(u.username) = lower(:username) or lower(u.email) = lower(:email))
            """)
    Optional<User> findWithDetailsByTenantAndIdentifier(
            @Param("tenantId") String tenantId,
            @Param("username") String username,
            @Param("email") String email
    );

    @Query(value = "select id from users where lower(username) = lower(:identifier) or lower(email) = lower(:identifier)", nativeQuery = true)
    Optional<String> findIdForLogin(@Param("identifier") String identifier);

    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    Optional<String> lockIdForSessionMutation(@Param("userId") String userId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "permissionOverrides"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findForLoginById(@Param("id") String id);

    default Optional<User> findForLogin(String identifier) {
        return findIdForLogin(identifier).flatMap(this::findForLoginById);
    }

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRecoveryEmailIgnoreCaseAndIdNot(String recoveryEmail, String id);

    Optional<User> findByIdAndTenantId(String id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.tenantId = :tenantId")
    Optional<User> lockForAuthzVersion(@Param("id") String id, @Param("tenantId") String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.tenantId = :tenantId and u.id in :ids order by u.id")
    List<User> lockAuthzVersions(
            @Param("tenantId") String tenantId,
            @Param("ids") Collection<String> ids);

    @Query("select u from User u where u.tenantId = :tenantId and u.id in :ids and upper(u.status) = 'ACTIVE'")
    List<User> findByTenantIdAndIdIn(
            @Param("tenantId") String tenantId,
            @Param("ids") Collection<String> ids);

    @Query("select u from User u where u.tenantId = :tenantId and u.id in :ids")
    List<User> findByTenantIdAndIdInAnyStatus(
            @Param("tenantId") String tenantId,
            @Param("ids") Collection<String> ids);

    Optional<User> findFirstByTenantIdOrderByCreatedAtAsc(String tenantId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "permissionOverrides", "accessScopes"})
    Optional<User> findWithDetailsByIdAndTenantId(String id, String tenantId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.auth.user.projection.UserListItemProjection(
                        u.id,
                        u.email,
                        u.fullName,
                        u.avatarUrl,
                        u.phone,
                        u.status,
                        u.provider,
                        u.accountType,
                        u.lastLoginProvider,
                        u.isProfileCompleted,
                        u.lastLoginAt,
                        u.createdAt
                    )
                    from User u
                    where u.tenantId = :tenantId
                    and (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.fullName, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%')))
                    and (:status is null or lower(u.status) = lower(:status))
                    and (:role is null or exists (
                        select 1 from u.roles r
                        where lower(r.name) = lower(:role)
                    ))
                    order by u.createdAt desc
                    """,
            countQuery = """
                    select count(u.id)
                    from User u
                    where u.tenantId = :tenantId
                    and (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.fullName, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%')))
                    and (:status is null or lower(u.status) = lower(:status))
                    and (:role is null or exists (
                        select 1 from u.roles r
                        where lower(r.name) = lower(:role)
                    ))
                    """
    )
    Page<UserListItemProjection> findAdminListItems(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("role") String role,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.auth.user.projection.UserRoleNameProjection(u.id, r.name)
            from User u
            join u.roles r
            where u.id in :userIds
            order by u.id asc, lower(r.name) asc
            """)
    List<UserRoleNameProjection> findRoleNamesByUserIds(@Param("userIds") Collection<String> userIds);

    @Query("""
            select distinct u
            from User u
            left join u.roles r
            left join u.accessScopes scope
            where u.tenantId = :tenantId
            and (u.status is null or lower(u.status) = 'active')
            and (
                lower(coalesce(r.name, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
                or lower(coalesce(scope.role, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
            )
            """)
    List<User> findCalendarTeachersByTenant(@Param("tenantId") String tenantId);

    @Query("""
            select distinct u from User u
            left join u.roles r
            left join u.accessScopes scope
            where u.tenantId = :tenantId
              and (u.status is null or lower(u.status) = 'active')
              and (lower(coalesce(r.name, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
                   or lower(coalesce(scope.role, '')) in ('teacher', 'lms.teacher', 'lms.instructor'))
            """)
    List<User> findCalendarTeachersByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("""
            select distinct u
            from User u
            left join u.roles r
            left join u.accessScopes scope
            where u.tenantId = :tenantId
            and (u.status is null or lower(u.status) = 'active')
            and (
                lower(coalesce(r.name, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
                or lower(coalesce(scope.role, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
            )
            and (
                scope.educationUnitId in :schoolIds
                or scope.educationUnitId is null
            )
            """)
    List<User> findCalendarTeachersByTenantAndSchoolIds(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") Collection<String> schoolIds
    );

    @Query("""
            select distinct u from User u
            left join u.roles r
            left join u.accessScopes scope
            where u.tenantId = :tenantId
              and (u.status is null or lower(u.status) = 'active')
              and (lower(coalesce(r.name, '')) in ('teacher', 'lms.teacher', 'lms.instructor')
                   or lower(coalesce(scope.role, '')) in ('teacher', 'lms.teacher', 'lms.instructor'))
              and (scope.educationUnitId in :schoolIds or scope.educationUnitId is null)
            """)
    List<User> findCalendarTeachersByTenantAndSchoolIds(
            @Param("tenantId") String tenantId,
            @Param("schoolIds") Collection<String> schoolIds,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.auth.user.projection.UserAccessRowProjection(
                        u.id,
                        u.fullName,
                        u.email,
                        u.status,
                        count(distinct r.id),
                        count(distinct s.id),
                        u.createdAt
                    )
                    from User u
                    left join u.roles r
                    left join u.accessScopes s
                    where u.tenantId = :tenantId
                    and (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.fullName, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(u.phone, '')) like lower(concat('%', :search, '%')))
                    and (:status is null or lower(u.status) = lower(:status))
                    and (:roleId is null or exists (
                        select 1 from u.roles roleFilter
                        where roleFilter.id = :roleId
                    ))
                    group by u.id, u.fullName, u.email, u.status, u.createdAt
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
                    and (:status is null or lower(u.status) = lower(:status))
                    and (:roleId is null or exists (
                        select 1 from u.roles roleFilter
                        where roleFilter.id = :roleId
                    ))
                    """
    )
    Page<UserAccessRowProjection> findAccessManagementRows(
            @Param("tenantId") String tenantId,
            @Param("search") String search,
            @Param("roleId") String roleId,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            select distinct new com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection(
                u.id,
                p.name,
                concat('role:', r.name)
            )
            from User u
            join u.roles r
            join r.permissions p
            where u.id in :userIds
            order by u.id asc, p.name asc
            """)
    List<UserEffectivePermissionProjection> findRoleEffectivePermissionsByUserIds(
            @Param("userIds") Collection<String> userIds
    );

    @Query("""
            select new com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection(
                u.id,
                override.permission,
                concat('override:', override.grantType)
            )
            from User u
            join u.permissionOverrides override
            where u.id in :userIds
            order by u.id asc, override.permission asc
            """)
    List<UserEffectivePermissionProjection> findOverrideEffectivePermissionsByUserIds(
            @Param("userIds") Collection<String> userIds
    );
}
