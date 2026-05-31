package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.AuthSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    Optional<AuthSession> findBySessionIdAndTenantId(String sessionId, String tenantId);

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions", "user.permissionOverrides", "user.accessScopes"})
    Optional<AuthSession> findWithUserBySessionIdAndTenantId(String sessionId, String tenantId);

    @EntityGraph(attributePaths = {"user", "user.roles", "user.roles.permissions", "user.permissionOverrides", "user.accessScopes"})
    Optional<AuthSession> findPrincipalByAccessTokenHashAndTenantId(String accessTokenHash, String tenantId);

    Optional<AuthSession> findByRefreshTokenHashAndTenantId(String refreshTokenHash, String tenantId);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AuthSession> findByAccessTokenHashAndTenantId(String accessTokenHash, String tenantId);

    List<AuthSession> findByUserIdAndTenantIdOrderByLastActiveAtDesc(String userId, String tenantId);

    List<AuthSession> findByUserIdAndTenantIdAndRevokedAtIsNull(String userId, String tenantId);

    List<AuthSession> findByUserIdAndTenantIdAndPortalAndRevokedAtIsNull(String userId, String tenantId, String portal);

    Optional<AuthSession> findBySessionIdAndUserIdAndTenantId(String sessionId, String userId, String tenantId);
}

