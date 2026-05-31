package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErgPrincipalService {

    private static final String DEFAULT_TENANT = "erg";

    private final AuthSessionRepository sessionRepository;
    private final AuthTokenService tokenService;

    public ErgPrincipalService(
            AuthSessionRepository sessionRepository,
            AuthTokenService tokenService
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "authPrincipal",
            key = "{#tenantId, #root.target.cacheTokenHash(#bearerToken)}",
            unless = "#result == null"
    )
    public Optional<ErgPrincipal> fromBearerToken(String bearerToken, String tenantId) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        String normalizedTenantId = tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
        return sessionRepository.findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken.trim()), normalizedTenantId)
                .filter(this::active)
                .map(this::principal);
    }

    public String cacheTokenHash(String bearerToken) {
        return bearerToken == null || bearerToken.isBlank() ? "" : tokenService.hashToken(bearerToken.trim());
    }

    private boolean active(AuthSession session) {
        User user = session.getUser();
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(OffsetDateTime.now())
                && user != null
                && "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

    private ErgPrincipal principal(AuthSession session) {
        User user = session.getUser();
        List<String> roles = roles(user);
        PermissionEnvelope permissions = permissions(user);
        List<String> managedEducationUnitIds = managedEducationUnitIds(user, roles);
        return new ErgPrincipal(
                user.getId(),
                session.getSessionId(),
                session.getTenantId(),
                roles,
                permissions.granted(),
                permissions.denied(),
                List.of(blankToDefault(session.getPortal(), "lms")),
                blankToDefault(user.getAccountType(), "erg"),
                roles.stream().anyMatch(role -> role.contains("admin")) ? "admin" : "user",
                Map.of("managedEducationUnitIds", managedEducationUnitIds)
        );
    }

    private List<String> roles(User user) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of("user");
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private PermissionEnvelope permissions(User user) {
        Set<String> granted = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        if (user != null && user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getPermissions() == null) {
                    continue;
                }
                for (Permission permission : role.getPermissions()) {
                    if (permission != null && permission.getName() != null && !permission.getName().isBlank()) {
                        granted.add(permission.getName().trim());
                    }
                }
            }
        }
        if (user != null && user.getPermissionOverrides() != null) {
            for (UserPermissionOverride override : user.getPermissionOverrides()) {
                if (override.getPermission() == null || override.getPermission().isBlank()) {
                    continue;
                }
                if (override.getExpiresAt() != null && override.getExpiresAt().isBefore(OffsetDateTime.now())) {
                    continue;
                }
                String permission = override.getPermission().trim();
                if (isGrant(override.getGrantType())) {
                    granted.add(permission);
                    denied.remove(permission);
                } else {
                    denied.add(permission);
                    granted.remove(permission);
                }
            }
        }
        return new PermissionEnvelope(List.copyOf(granted), List.copyOf(denied));
    }

    private List<String> managedEducationUnitIds(User user, List<String> roles) {
        if (roles.stream().anyMatch(role -> role.toLowerCase(Locale.ROOT).contains("admin"))) {
            return List.of("*");
        }
        if (user == null || user.getAccessScopes() == null) {
            return List.of();
        }
        return user.getAccessScopes().stream()
                .filter(scope -> scope.getEducationUnitId() != null && !scope.getEducationUnitId().isBlank())
                .filter(this::appliesToLms)
                .map(scope -> scope.getEducationUnitId().trim())
                .distinct()
                .toList();
    }

    private boolean appliesToLms(UserAccessScope scope) {
        String modules = scope.getModules() == null ? "" : scope.getModules().trim().toLowerCase(Locale.ROOT);
        return modules.isBlank() || modules.equals("*") || modules.contains("lms");
    }

    private boolean isGrant(String grantType) {
        return grantType == null || grantType.isBlank()
                || "grant".equalsIgnoreCase(grantType)
                || "allow".equalsIgnoreCase(grantType);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record PermissionEnvelope(List<String> granted, List<String> denied) {
    }
}
