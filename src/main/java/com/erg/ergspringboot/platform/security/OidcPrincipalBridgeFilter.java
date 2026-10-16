package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "erg.auth.bff-enabled", havingValue = "true")
public class OidcPrincipalBridgeFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final OidcMfaPolicy mfaPolicy;
    private final String configuredTenantId;

    @Autowired
    public OidcPrincipalBridgeFilter(
            UserRepository userRepository,
            ErgAuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.mfaPolicy = new OidcMfaPolicy();
        this.configuredTenantId = AuthenticatedTenant.require(authProperties.getBffTenantId());
    }

    /** Compatibility constructor retained for focused unit tests and old embedders. */
    public OidcPrincipalBridgeFilter(UserRepository userRepository) {
        this(userRepository, "erg");
    }

    /** Compatibility constructor retained for old embedders. */
    public OidcPrincipalBridgeFilter(UserRepository userRepository, String configuredTenantId) {
        this.userRepository = userRepository;
        this.mfaPolicy = new OidcMfaPolicy();
        this.configuredTenantId = AuthenticatedTenant.require(configuredTenantId);
    }

    @Override
    @Transactional(readOnly = true)
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String identifier = identifier(oidcUser);
            String tenantId = resolveTenant(oidcUser);
            String requestedTenantId = SecurityUtils.trimToNull(request.getHeader("X-Tenant-ID"));
            if (requestedTenantId != null && !tenantId.equals(requestedTenantId)) {
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            User user = identifier == null ? null : userRepository
                    .findWithDetailsByTenantAndIdentifier(tenantId, identifier, identifier)
                    .orElse(null);
            if (user == null
                    || !tenantId.equals(user.getTenantId())
                    || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                SecurityContextHolder.clearContext();
            } else {
                List<String> roles = roleNames(user);
                if (!mfaPolicy.satisfied(roles, oidcUser.getClaims())) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "MFA_REQUIRED");
                    return;
                }
                ErgPrincipal principal = principal(user, request.getSession().getId());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal,
                        authentication.getCredentials(),
                        authentication.getAuthorities()
                ));
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Tenant identity comes from a verified OIDC claim or server configuration. The
     * legacy request header is accepted only as a consistency check, never as the
     * source of tenant authorization.
     */
    private String resolveTenant(OidcUser oidcUser) {
        if (oidcUser != null && oidcUser.getClaims() != null) {
            for (String claimName : List.of("tenant_id", "tenantId", "tenant")) {
                Object value = oidcUser.getClaims().get(claimName);
                if (value instanceof String string && !string.isBlank()) {
                    return AuthenticatedTenant.require(string);
                }
            }
        }
        return configuredTenantId;
    }

    private String identifier(OidcUser oidcUser) {
        String username = oidcUser.getPreferredUsername();
        if (username != null && !username.isBlank()) return username.trim().toLowerCase(Locale.ROOT);
        String email = oidcUser.getEmail();
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private ErgPrincipal principal(User user, String sessionId) {
        List<String> roles = roleNames(user);
        Set<String> permissions = new TreeSet<>();
        if (user.getRoles() != null) user.getRoles().stream()
                .flatMap(role -> role.getPermissions() == null ? java.util.stream.Stream.empty() : role.getPermissions().stream())
                .map(Permission::getName).filter(java.util.Objects::nonNull).map(String::trim).map(String::toLowerCase)
                .forEach(permissions::add);
        Set<String> denied = new LinkedHashSet<>();
        if (user.getPermissionOverrides() != null) {
            for (UserPermissionOverride override : user.getPermissionOverrides()) {
                if (override.getPermission() == null || override.getPermission().isBlank()) continue;
                if (override.getExpiresAt() != null && override.getExpiresAt().isBefore(OffsetDateTime.now())) continue;
                String permission = override.getPermission().trim().toLowerCase(Locale.ROOT);
                if ("deny".equalsIgnoreCase(override.getGrantType())) {
                    permissions.remove(permission);
                    denied.add(permission);
                } else {
                    denied.remove(permission);
                    permissions.add(permission);
                }
            }
        }
        List<String> derivedPortals = WorkspaceCapabilityMapper.legacyPortalsFor(
                List.of(), roles, List.copyOf(permissions));
        return ErgPrincipal.authenticated(
                user.getId(), sessionId, user.getTenantId(), roles, List.copyOf(permissions), List.copyOf(denied),
                derivedPortals, user.getAccountType(), permissions.contains("*") ? "admin" : "user",
                AccessScopeAssembler.mergeLegacyAttributes(Map.of(
                        "email", user.getEmail() == null ? "" : user.getEmail(),
                        "fullName", user.getFullName() == null ? "" : user.getFullName()
                ), AccessScopeSet.legacy(managedEducationUnitIds(user), List.of(), null, null))
        );
    }

    private List<String> managedEducationUnitIds(User user) {
        if (user == null || user.getAccessScopes() == null) return List.of();
        return user.getAccessScopes().stream()
                .filter(this::appliesToLms)
                .map(UserAccessScope::getEducationUnitId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean appliesToLms(UserAccessScope scope) {
        String modules = scope.getModules() == null ? "" : scope.getModules().trim().toLowerCase(Locale.ROOT);
        return modules.isBlank() || modules.equals("*") || modules.contains("lms");
    }

    private List<String> roleNames(User user) {
        return user.getRoles() == null ? List.of() : user.getRoles().stream()
                .map(Role::getName).filter(java.util.Objects::nonNull).map(String::trim).map(String::toLowerCase)
                .distinct().sorted().toList();
    }
}
