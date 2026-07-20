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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErgPrincipalService {

    private static final String DEFAULT_TENANT = "erg";
    private final AuthSessionRepository sessionRepository;
    private final AuthTokenService tokenService;
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    public ErgPrincipalService(
            AuthSessionRepository sessionRepository,
            AuthTokenService tokenService
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<String> authenticationFailureCode(String bearerToken, String tenantId) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        String normalizedTenantId = tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
        return sessionRepository
                .findByAccessTokenHashAndTenantId(tokenService.hashToken(bearerToken.trim()), normalizedTenantId)
                .filter(session -> session.getRevokedAt() != null)
                .map(AuthSession::getRevokedReason)
                .map(this::authenticationFailureCodeForReason)
                .filter(java.util.Objects::nonNull);
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
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(OffsetDateTime.now())
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
        return new ErgPrincipal(
                user.getId(),
                session.getSessionId(),
                session.getTenantId(),
                roles,
                permissions.granted(),
                permissions.denied(),
                effectivePortals(session, permissions),
                blankToDefault(user.getAccountType(), "erg"),
                permissions.granted().contains("*") ? "admin" : "user",
                Map.of(
                        "managedEducationUnitIds", managedEducationUnitIds,
                        "managedClassIds", assignedClassIds(assignments),
                        "assignedScopeIds", assignedScopeIds(assignments),
                        "lifecycleRestricted", isLifecycleRestricted(user)
                )
        );
    }

    private boolean isLifecycleRestricted(User user) {
        return !Boolean.TRUE.equals(user.getIsProfileCompleted())
                || !Boolean.TRUE.equals(user.getRecoveryEmailVerified())
                || Boolean.TRUE.equals(user.getMustChangePassword());
    }

    private List<String> effectivePortals(
            AuthSession session,
            PermissionEnvelope permissions
    ) {
        boolean platformWide = permissions.granted().contains("*");
        return platformWide
                ? List.of("*")
                : List.of(blankToDefault(session.getPortal(), "lms"));
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
                user.getId(), blankToDefault(user.getTenantId(), DEFAULT_TENANT), OffsetDateTime.now());
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
