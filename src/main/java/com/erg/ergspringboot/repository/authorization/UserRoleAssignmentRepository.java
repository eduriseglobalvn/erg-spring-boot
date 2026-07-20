package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, String> {

    @EntityGraph(attributePaths = {"role"})
    @Query("select assignment from UserRoleAssignment assignment where assignment.id = :assignmentId")
    Optional<UserRoleAssignment> findWithRoleById(@Param("assignmentId") String assignmentId);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    @Query("""
            select assignment from UserRoleAssignment assignment
            where assignment.userId = :userId
              and assignment.tenantId = :tenantId
              and assignment.revokedAt is null
              and (assignment.expiresAt is null or assignment.expiresAt > :now)
            order by assignment.createdAt asc
            """)
    List<UserRoleAssignment> findActiveForUser(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            select assignment from UserRoleAssignment assignment
            where assignment.roleId = :roleId
              and assignment.revokedAt is null
              and (assignment.expiresAt is null or assignment.expiresAt > :now)
            order by assignment.tenantId asc, assignment.userId asc
            """)
    List<UserRoleAssignment> findActiveForRole(
            @Param("roleId") String roleId,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            select assignment from UserRoleAssignment assignment
            where assignment.roleId in :roleIds
              and assignment.revokedAt is null
              and (assignment.expiresAt is null or assignment.expiresAt > :now)
            order by assignment.roleId asc, assignment.tenantId asc, assignment.userId asc
            """)
    List<UserRoleAssignment> findActiveForRoleIds(
            @Param("roleIds") Collection<String> roleIds,
            @Param("now") OffsetDateTime now
    );

    List<UserRoleAssignment> findByUserIdAndTenantIdAndRevokedAtIsNull(String userId, String tenantId);

    @EntityGraph(attributePaths = {"role"})
    @Query("""
            select assignment from UserRoleAssignment assignment
            where assignment.tenantId = :tenantId
              and assignment.userId in :userIds
              and assignment.revokedAt is null
              and (assignment.expiresAt is null or assignment.expiresAt > :now)
            order by assignment.userId asc, assignment.createdAt asc
            """)
    List<UserRoleAssignment> findActiveForUsers(
            @Param("tenantId") String tenantId,
            @Param("userIds") Collection<String> userIds,
            @Param("now") OffsetDateTime now);
}
