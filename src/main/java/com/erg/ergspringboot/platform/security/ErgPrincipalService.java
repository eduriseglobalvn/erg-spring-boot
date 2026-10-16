package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErgPrincipalService {

    private final AuthSessionRepository sessionRepository;
    private final AuthTokenService tokenService;
    private final AuthSessionTenantResolver tenantResolver;
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    public ErgPrincipalService(
            AuthSessionRepository sessionRepository,
            AuthTokenService tokenService,
            AuthSessionTenantResolver tenantResolver
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
        this.tenantResolver = tenantResolver;
    }

    /** Compatibility constructor for focused unit tests and old embedders. */
    public ErgPrincipalService(
            AuthSessionRepository sessionRepository,
            AuthTokenService tokenService
    ) {
        this(sessionRepository, tokenService, null);
    }

    @Transactional(readOnly = true)
    public Optional<ErgPrincipal> fromBearerToken(String bearerToken, String tenantId) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        String requestedTenantId = SecurityUtils.trimToNull(tenantId);
        if (requestedTenantId == null) {
            return fromBearerToken(bearerToken);
        }
        // Focused embedders/tests may construct this service without the JDBC
        // resolver. Keep that compatibility path fully in-memory and fail
        // closed on a tenant mismatch; production always uses the resolver
        // before entering the tenant-scoped repository query below.
        if (tenantResolver == null) {
            return uniqueSession(bearerToken, false)
                    .filter(session -> requestedTenantId.equals(session.getTenantId()))
                    .map(this::principal);
        }
        Optional<String> resolvedTenantId = resolveTenantId(bearerToken);
        if (!resolvedTenantId.filter(requestedTenantId::equals).isPresent()) {
            return Optional.empty();
        }
        return sessionRepository
                .findPrincipalByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken.trim()), requestedTenantId)
                .filter(this::active)
                .map(this::principal);
    }

    /**
     * Resolves the tenant from the server-side session row keyed by the opaque
     * access-token hash. A token must map to exactly one active session; an
     * absent or ambiguous result is rejected instead of falling back to the
     * default ERG tenant.
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveTenantId(String bearerToken) {
        if (tenantResolver != null) {
            if (bearerToken == null || bearerToken.isBlank()) {
                return Optional.empty();
            }
            return tenantResolver.resolve(tokenService.hashToken(bearerToken.trim()));
        }
        return uniqueSession(bearerToken, false).map(AuthSession::getTenantId);
    }

    public Optional<ErgPrincipal> fromBearerToken(String bearerToken) {
        return resolveTenantId(bearerToken).flatMap(tenantId -> {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(
                    AuthenticatedTenant.require(tenantId), null, null))) {
                return fromBearerToken(bearerToken, tenantId);
            }
        });
    }

    public String cacheTokenHash(String bearerToken) {
        return bearerToken == null || bearerToken.isBlank() ? "" : tokenService.hashToken(bearerToken.trim());
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<String> authenticationFailureCode(String bearerToken, String tenantId) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        Optional<String> resolved = authenticationFailureCode(bearerToken);
        String requestedTenantId = SecurityUtils.trimToNull(tenantId);
        return requestedTenantId == null
                ? resolved
                : resolved.filter(code -> matchesRequestedTenant(bearerToken, requestedTenantId));
    }

    /** Resolves a revoked-session diagnostic without a client tenant lookup key. */
    @Transactional(readOnly = true)
    public Optional<String> authenticationFailureCode(String bearerToken) {
        if (tenantResolver != null) {
            return Optional.empty();
        }
        return uniqueSession(bearerToken, true)
                .filter(session -> session.getRevokedAt() != null)
                .map(AuthSession::getRevokedReason)
                .map(this::authenticationFailureCodeForReason)
                .filter(java.util.Objects::nonNull);
    }

    private boolean matchesRequestedTenant(String bearerToken, String requestedTenantId) {
        return fromBearerToken(bearerToken)
                .map(principal -> requestedTenantId.equals(principal.tenantId()))
                .orElseGet(() -> uniqueSession(bearerToken, true)
                        .map(session -> requestedTenantId.equals(session.getTenantId()))
                        .orElse(false));
    }

    private Optional<AuthSession> uniqueSession(String bearerToken, boolean includeRevoked) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        List<AuthSession> sessions = sessionRepository.findByAccessTokenHash(tokenService.hashToken(bearerToken.trim()));
        if (sessions == null || sessions.isEmpty()) {
            return Optional.empty();
        }
        List<AuthSession> candidates = sessions.stream()
                .filter(session -> session != null && (includeRevoked || active(session)))
                .filter(session -> session.getTenantId() != null && !session.getTenantId().isBlank())
                // Entity graphs may repeat the same session row for each
                // fetched role/permission. Collapse those duplicates before
                // applying the ambiguity check; distinct session IDs remain
                // fail-closed.
                .distinct()
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    private String authenticationFailureCodeForReason(String reason) {
        if ("session_replaced".equalsIgnoreCase(reason)) {
            return "AUTH_SESSION_REPLACED";
        }
        if ("status_changed_inactive".equalsIgnoreCase(reason)) {
            return "ACCOUNT_DISABLED";
        }
        return null;
    }

    private boolean active(AuthSession session) {
        User user = session.getUser();
        OffsetDateTime now = OffsetDateTime.now();
        // Short-lived access token expiry is the primary gate; sessions issued before this column
        // existed have a null value and fall back to the session-level expiry so a deploy doesn't
        // log everyone out mid-session.
        OffsetDateTime accessExpiry = session.getAccessTokenExpiresAt();
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now)
                && (accessExpiry == null || accessExpiry.isAfter(now))
                && user != null
                && "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

    private ErgPrincipal principal(AuthSession session) {
        User user = session.getUser();
        List<UserRoleAssignment> assignments = activeAssignments(user);
        List<String> roles = roles(user, assignments);
        PermissionEnvelope permissions = permissions(user);
        permissions = permissions.withAssignments(assignments);
        List<String> managedEducationUnitIds = managedEducationUnitIds(user, permissions, assignments);
        return ErgPrincipal.authenticated(
                user.getId(),
                session.getSessionId(),
                session.getTenantId(),
                roles,
                permissions.granted(),
                permissions.denied(),
                effectivePortals(roles, permissions),
                blankToDefault(user.getAccountType(), "erg"),
                permissions.granted().contains("*") ? "admin" : "user",
                AccessScopeAssembler.mergeLegacyAttributes(Map.of(
                        "assignedScopeIds", assignedScopeIds(assignments),
                        "lifecycleRestricted", isLifecycleRestricted(user)
                ), AccessScopeSet.legacy(managedEducationUnitIds, assignedClassIds(assignments), null, null))
        );
    }

    private boolean isLifecycleRestricted(User user) {
        return !Boolean.TRUE.equals(user.getIsProfileCompleted())
                || !Boolean.TRUE.equals(user.getRecoveryEmailVerified())
                || Boolean.TRUE.equals(user.getMustChangePassword());
    }

    private List<String> effectivePortals(
            List<String> roles,
            PermissionEnvelope permissions
    ) {
        return WorkspaceCapabilityMapper.legacyPortalsFor(List.of(), roles, permissions.granted());
    }

    private List<String> roles(User user, List<UserRoleAssignment> assignments) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            names.add("user");
        } else {
            user.getRoles().stream()
                    .map(Role::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .forEach(names::add);
        }
        assignments.stream().map(UserRoleAssignment::getRole).filter(java.util.Objects::nonNull)
                .map(Role::getName).filter(name -> name != null && !name.isBlank())
                .map(String::trim).map(String::toLowerCase).forEach(names::add);
        return List.copyOf(names);
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
                        granted.add(permission.getName().trim().toLowerCase(Locale.ROOT));
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
                String permission = override.getPermission().trim().toLowerCase(Locale.ROOT);
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

    private List<UserRoleAssignment> activeAssignments(User user) {
        if (userRoleAssignmentRepository == null || user == null || user.getId() == null) return List.of();
        return userRoleAssignmentRepository.findActiveForUser(
                user.getId(), AuthenticatedTenant.require(user.getTenantId()), OffsetDateTime.now());
    }

    private List<String> assignedScopeIds(List<UserRoleAssignment> assignments) {
        return assignments.stream()
                .filter(assignment -> assignment.getScopeId() != null && !assignment.getScopeId().isBlank())
                .map(UserRoleAssignment::getScopeId)
                .distinct()
                .toList();
    }

    private List<String> assignedClassIds(List<UserRoleAssignment> assignments) {
        return assignments.stream()
                .filter(assignment -> "CLASS".equals(assignment.getScopeType()))
                .map(UserRoleAssignment::getScopeId)
                .filter(scopeId -> scopeId != null && !scopeId.isBlank())
                .distinct()
                .toList();
    }

    private List<String> managedEducationUnitIds(
            User user,
            PermissionEnvelope permissions,
            List<UserRoleAssignment> assignments
    ) {
        if (permissions.granted().contains("*")) {
            return List.of("*");
        }
        Set<String> managedIds = new LinkedHashSet<>();
        if (user != null && user.getAccessScopes() != null) {
            user.getAccessScopes().stream()
                    .filter(scope -> scope.getEducationUnitId() != null && !scope.getEducationUnitId().isBlank())
                    .filter(this::appliesToLms)
                    .map(scope -> scope.getEducationUnitId().trim())
                    .forEach(managedIds::add);
        }
        assignments.stream()
                .filter(assignment -> Set.of("CENTER", "SCHOOL").contains(assignment.getScopeType()))
                .map(UserRoleAssignment::getScopeId)
                .filter(scopeId -> scopeId != null && !scopeId.isBlank())
                .map(String::trim)
                .forEach(managedIds::add);
        return List.copyOf(managedIds);
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
        PermissionEnvelope withAssignments(List<UserRoleAssignment> assignments) {
            if (assignments == null || assignments.isEmpty()) return this;
            Set<String> nextGranted = new LinkedHashSet<>(granted);
            Set<String> nextDenied = new LinkedHashSet<>(denied);
            assignments.stream().map(UserRoleAssignment::getRole).filter(java.util.Objects::nonNull)
                    .flatMap(role -> role.getPermissions() == null ? java.util.stream.Stream.empty() : role.getPermissions().stream())
                    .map(Permission::getName).filter(name -> name != null && !name.isBlank())
                    .map(name -> name.trim().toLowerCase(Locale.ROOT)).forEach(permission -> {
                        nextGranted.add(permission);
                        nextDenied.remove(permission);
                    });
            return new PermissionEnvelope(List.copyOf(nextGranted), List.copyOf(nextDenied));
        }
    }
}
