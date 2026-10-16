package com.erg.ergspringboot.users.internal.authorization;

import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserRoleNameProjection;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.users.UserPermissionOverrideProjection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Compatibility adapter for authorization reads while users persistence is legacy JPA. */
@Service
@Deprecated(since = "2026-08-08", forRemoval = false)
public final class LegacyUserAuthorizationAdapter implements UserAuthorizationPort {

    private final UserRepository users;
    private final UserPermissionOverrideRepository overrides;

    public LegacyUserAuthorizationAdapter(
            UserRepository users, UserPermissionOverrideRepository overrides) {
        this.users = users;
        this.overrides = overrides;
    }

    @Override
    public Optional<UserAuthorizationProjection> find(String userId, String tenantId) {
        String scopedTenant = normalizeTenant(tenantId);
        return users.findByIdAndTenantId(userId, scopedTenant)
                .map(ignored -> snapshot(userId, scopedTenant));
    }

    @Override
    public List<UserPermissionOverrideProjection> findOverrides(String userId, String tenantId) {
        if (find(userId, tenantId).isEmpty()) {
            return List.of();
        }
        return overrides.findByUserId(userId).stream().map(this::map).toList();
    }

    private UserAuthorizationProjection snapshot(String userId, String tenantId) {
        List<String> roleNames = users.findRoleNamesByUserIds(List.of(userId)).stream()
                .map(UserRoleNameProjection::roleName)
                .filter(this::nonBlank)
                .distinct()
                .toList();
        List<String> rolePermissions = users.findRoleEffectivePermissionsByUserIds(List.of(userId)).stream()
                .filter(row -> row.source() != null && row.source().startsWith("role:"))
                .map(UserEffectivePermissionProjection::permission)
                .filter(this::nonBlank)
                .distinct()
                .toList();
        List<UserPermissionOverrideProjection> overrideRows = findOverridesWithoutExistenceCheck(userId);
        List<String> grants = overrideRows.stream()
                .filter(row -> "GRANT".equalsIgnoreCase(row.grantType()))
                .map(UserPermissionOverrideProjection::permission)
                .filter(this::nonBlank)
                .toList();
        List<String> denies = overrideRows.stream()
                .filter(row -> "DENY".equalsIgnoreCase(row.grantType()))
                .map(UserPermissionOverrideProjection::permission)
                .filter(this::nonBlank)
                .toList();
        return new UserAuthorizationProjection(userId, tenantId, roleNames, rolePermissions, grants, denies);
    }

    private List<UserPermissionOverrideProjection> findOverridesWithoutExistenceCheck(String userId) {
        return overrides.findByUserId(userId).stream().map(this::map).toList();
    }

    private UserPermissionOverrideProjection map(UserPermissionOverride value) {
        return new UserPermissionOverrideProjection(
                value.getId(), value.getUserId(), value.getPermission(), value.getGrantType(),
                value.getReason(), value.getExpiresAt(), value.getCreatedBy(), value.getCreatedAt());
    }

    private String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "erg" : value.trim();
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
