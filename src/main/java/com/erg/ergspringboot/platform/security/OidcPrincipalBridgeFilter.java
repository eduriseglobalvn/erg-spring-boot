package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
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

    public OidcPrincipalBridgeFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.mfaPolicy = new OidcMfaPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String identifier = identifier(oidcUser);
            String tenantId = SecurityUtils.normalizeTenant(request.getHeader("X-Tenant-ID"));
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
        Set<String> portals = new TreeSet<>();
        permissions.forEach(permission -> {
            int separator = permission.indexOf('.');
            if (separator > 0) {
                String namespace = permission.substring(0, separator);
                if (Set.of("admin", "crm", "lcms", "lms", "elearning").contains(namespace)) portals.add(namespace);
            }
        });
        if (permissions.contains("*")) {
            portals.clear();
            portals.add("*");
        }
        return new ErgPrincipal(
                user.getId(), sessionId, user.getTenantId(), roles, List.copyOf(permissions), List.copyOf(denied),
                List.copyOf(portals), user.getAccountType(), permissions.contains("*") ? "admin" : "user",
                Map.of(
                        "email", user.getEmail() == null ? "" : user.getEmail(),
                        "fullName", user.getFullName() == null ? "" : user.getFullName()
                )
        );
    }

    private List<String> roleNames(User user) {
        return user.getRoles() == null ? List.of() : user.getRoles().stream()
                .map(Role::getName).filter(java.util.Objects::nonNull).map(String::trim).map(String::toLowerCase)
                .distinct().sorted().toList();
    }
}
