package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AuthSession;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    Optional<AuthSession> findBySessionIdAndTenantId(String sessionId, String tenantId);

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions", "user.permissionOverrides", "user.accessScopes"})
    Optional<AuthSession> findWithUserBySessionIdAndTenantId(String sessionId, String tenantId);

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions", "user.permissionOverrides", "user.accessScopes"})
    Optional<AuthSession> findPrincipalByAccessTokenHashAndTenantId(String accessTokenHash, String tenantId);

    /**
     * Resolves the tenant from the opaque access-token hash without accepting a
     * client supplied tenant as the lookup key. The security boundary rejects
     * ambiguous results before constructing a principal.
     */
    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions", "user.permissionOverrides", "user.accessScopes"})
    List<AuthSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AuthSession> findByRefreshTokenHashAndTenantId(String refreshTokenHash, String tenantId);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session where session.refreshTokenHash = :refreshTokenHash")
    Optional<AuthSession> lockByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    Optional<AuthSession> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);

    Optional<AuthSession> findByAccessTokenHashAndTenantId(String accessTokenHash, String tenantId);

    List<AuthSession> findByUserIdAndTenantIdOrderByLastActiveAtDesc(String userId, String tenantId);

    List<AuthSession> findByUserIdAndTenantIdAndRevokedAtIsNull(String userId, String tenantId);

    Optional<AuthSession> findBySessionIdAndUserIdAndTenantId(String sessionId, String userId, String tenantId);

    List<AuthSession> findByUserIdInAndTenantIdAndRevokedAtIsNull(Collection<String> userIds, String tenantId);

    @Query("""
            select session from AuthSession session
            where session.tenantId = :tenantId
            order by coalesce(session.revokedAt, session.createdAt) desc, session.id desc
            """)
    Page<AuthSession> findSecurityFeedByTenant(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("""
            select session from AuthSession session
            where session.tenantId = :tenantId and session.userId = :userId
            order by coalesce(session.revokedAt, session.createdAt) desc, session.id desc
            """)
    Page<AuthSession> findSecurityFeedByTenantAndUser(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            Pageable pageable);
}
