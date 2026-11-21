package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.AuthSession;
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

    /** One bounded retention batch per call, in its own transaction; a result below {@code batch} marks the tail. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "delete from user_sessions where id in (select id from user_sessions"
            + " where expires_at < :cutoff or revoked_at < :cutoff limit :batch)", nativeQuery = true)
    int deleteDeadBefore(@Param("cutoff") java.time.OffsetDateTime cutoff, @Param("batch") int batch);

    /** The newest 50: a listing never needs a user's whole session history. */
    List<AuthSession> findTop50ByUserIdAndTenantIdOrderByLastActiveAtDesc(String userId, String tenantId);

    /** Unrevoked AND unexpired: expired rows are not purged inline and would be hydrated on every login. */
    @Query("""
            select s from AuthSession s
            where s.userId = :userId and s.tenantId = :tenantId and s.revokedAt is null and s.expiresAt > current_timestamp
            """)
    List<AuthSession> findByUserIdAndTenantIdAndRevokedAtIsNull(
            @Param("userId") String userId, @Param("tenantId") String tenantId);

    Optional<AuthSession> findBySessionIdAndUserIdAndTenantId(String sessionId, String userId, String tenantId);

    @Query("""
            select s from AuthSession s
            where s.userId in :userIds and s.tenantId = :tenantId and s.revokedAt is null and s.expiresAt > current_timestamp
            """)
    List<AuthSession> findByUserIdInAndTenantIdAndRevokedAtIsNull(
            @Param("userIds") Collection<String> userIds, @Param("tenantId") String tenantId);

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
