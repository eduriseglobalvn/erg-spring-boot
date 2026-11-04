package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
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
    private UserRoleAssignmentRepository roleAssignments;
    private OidcIdentityLinkStore identityLinks;

    @Autowired
    public OidcPrincipalBridgeFilter(
            UserRepository userRepository,
            ErgAuthProperties authProperties,
            ObjectProvider<UserRoleAssignmentRepository> roleAssignments,
            ObjectProvider<OidcIdentityLinkStore> identityLinks
    ) {
        this.userRepository = userRepository;
        this.mfaPolicy = new OidcMfaPolicy();
        this.configuredTenantId = AuthenticatedTenant.require(authProperties.getBffTenantId());
        this.roleAssignments = roleAssignments.getIfAvailable();
        this.identityLinks = identityLinks.getIfAvailable();
    }

    /** Test seam for the optional collaborators wired by the Spring constructor. */
    void setCollaborators(UserRoleAssignmentRepository roleAssignments, OidcIdentityLinkStore identityLinks) {
        this.roleAssignments = roleAssignments;
        this.identityLinks = identityLinks;
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
            User user = resolveUser(oidcUser, tenantId, identifier);
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

    /**
     * Identity linking: a stored (issuer, subject) link wins. Otherwise the IdP username must equal
     * the internal username, or the IdP email must be verified and equal the internal email; the
     * first successful match is then pinned to the subject so later logins cannot be re-pointed by
     * an IdP user who edits their own username or email.
     */
    private User resolveUser(OidcUser oidcUser, String tenantId, String identifier) {
        String issuer = oidcUser.getIssuer() == null ? null : oidcUser.getIssuer().toString();
        String subject = SecurityUtils.trimToNull(oidcUser.getSubject());
        boolean linkable = identityLinks != null && issuer != null && subject != null;
        if (linkable) {
            Optional<String> linkedUserId = identityLinks.linkedUserId(tenantId, issuer, subject);
            if (linkedUserId.isPresent()) {
                return userRepository.findWithDetailsByIdAndTenantId(linkedUserId.get(), tenantId).orElse(null);
            }
        }
        if (identifier == null) {
            return null;
        }
        User user = userRepository.findWithDetailsByTenantAndIdentifier(tenantId, identifier, identifier).orElse(null);
        if (user == null || !matchesTrustedIdentifier(user, oidcUser, identifier)) {
            return null;
        }
        if (linkable && !identityLinks.link(tenantId, issuer, subject, user.getId())) {
            return null;
        }
        return user;
    }

    private boolean matchesTrustedIdentifier(User user, OidcUser oidcUser, String identifier) {
        if (user.getUsername() != null && user.getUsername().trim().equalsIgnoreCase(identifier)) {
            return true;
        }
        String email = verifiedEmail(oidcUser);
        return email != null && user.getEmail() != null && user.getEmail().trim().equalsIgnoreCase(email);
    }

    private String identifier(OidcUser oidcUser) {
        String username = oidcUser.getPreferredUsername();
        if (username != null && !username.isBlank()) return username.trim().toLowerCase(Locale.ROOT);
        return verifiedEmail(oidcUser);
    }

    private String verifiedEmail(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ErgPrincipal principal(User user, String sessionId) {
        List<String> roles = roleNames(user);
        List<UserRoleAssignment> assignments = roleAssignments == null ? List.of()
                : roleAssignments.findActiveForUser(user.getId(), user.getTenantId(), OffsetDateTime.now());
        EffectivePermissionResolver.Result resolved =
                EffectivePermissionResolver.resolve(user, assignments, OffsetDateTime.now());
        List<String> permissions = resolved.granted();
        List<String> denied = resolved.denied();
        List<String> derivedPortals = WorkspaceCapabilityMapper.legacyPortalsFor(List.of(), roles, permissions);
        return ErgPrincipal.authenticated(
                user.getId(), sessionId, user.getTenantId(), roles, permissions, denied,
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
                .filter(AccessScopeAssembler::appliesToLms)
                .map(UserAccessScope::getEducationUnitId)
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> roleNames(User user) {
        return user.getRoles() == null ? List.of() : user.getRoles().stream()
                .map(Role::getName).filter(java.util.Objects::nonNull).map(String::trim).map(String::toLowerCase)
                .distinct().sorted().toList();
    }
}
