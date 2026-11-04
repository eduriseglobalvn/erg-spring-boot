package com.erg.ergspringboot.content.lifecycle;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.content.persistence.ContentLifecycleTransitionRepository;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access enforcement for {@link ContentLifecycleOperations}: the access context must match the
 * authenticated principal, each target state needs its permission, and a review submitter cannot
 * approve their own submission without elevated permission.
 */
final class ContentLifecycleAccessPolicy {

    private static final Set<String> ELEVATED = Set.of("content.manage", "curriculum.manage", "content.lifecycle.manage");

    private final ContentLifecycleTransitionRepository transitions;
    private final AuthorizationOperations authorization;

    ContentLifecycleAccessPolicy(ContentLifecycleTransitionRepository transitions, AuthorizationOperations authorization) {
        this.transitions = transitions;
        this.authorization = authorization;
    }

    void requireAccess(UserAccessContext access, String resourceId) {
        if (access == null || blankToNull(access.tenantId()) == null || blankToNull(access.userId()) == null) {
            throw new ForbiddenException("authenticated tenant context is required");
        }
        if (blankToNull(resourceId) == null) throw new BadRequestException("resourceId is required");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ErgPrincipal)) {
            throw new ForbiddenException("authenticated principal is required");
        }
        ErgPrincipal principal = (ErgPrincipal) authentication.getPrincipal();
        if (!access.tenantId().equals(principal.tenantId())
                || !access.userId().equals(principal.userId())) {
            throw new ForbiddenException("access context does not match the authenticated principal");
        }
    }

    void authorize(LifecycleState target, LifecycleResourceKind kind, String tenantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ErgPrincipal principal)
                || principal.tenantId() == null || principal.tenantId().isBlank()) {
            throw new ForbiddenException("authenticated principal is required");
        }
        String required = switch (target) {
            case IN_REVIEW -> "content.lifecycle.submit";
            case APPROVED -> "content.lifecycle.approve";
            case PUBLISHED -> kind == LifecycleResourceKind.QUIZ ? "lms.quiz.publish" : "content.lifecycle.publish";
            case ARCHIVED -> "content.lifecycle.archive";
            case DRAFT -> "content.lifecycle.rollback";
        };
        if (!hasPermission(principal, required, kind, tenantId)) {
            throw new ForbiddenException("missing permission " + required);
        }
    }

    private boolean hasPermission(ErgPrincipal principal, String required, LifecycleResourceKind kind, String tenantId) {
        if (authorization == null) {
            Set<String> permissions = principal.permissions() == null ? Set.of() : Set.copyOf(principal.permissions());
            return permissions.stream().map(this::normalize).anyMatch(required::equals);
        }
        if (required.equals("lms.quiz.publish") && kind == LifecycleResourceKind.QUIZ) {
            return authorization.isAllowed(principal, required, "teacher", tenantId)
                    || authorization.isAllowed(principal, "content.lifecycle.publish", "admin", tenantId)
                    || authorization.isAllowed(principal, "content.manage", "admin", tenantId);
        }
        return authorization.isAllowed(principal, required, "admin", tenantId)
                || authorization.isAllowed(principal, "content.manage", "admin", tenantId)
                || authorization.isAllowed(principal, "curriculum.manage", "admin", tenantId);
    }

    void enforceApprovalSeparation(String tenantId, String actorId, LifecycleResourceKind kind, String resourceId, LifecycleState target) {
        if (target != LifecycleState.APPROVED || hasElevatedPermission()) return;
        transitions.findTopByTenantIdAndResourceKindAndResourceIdAndToStateOrderByCreatedAtDesc(
                        tenantId, kind.name(), resourceId, LifecycleState.IN_REVIEW.name())
                .filter(last -> actorId.equals(last.getActorId()))
                .ifPresent(last -> { throw new ForbiddenException("review submitter cannot approve without elevated permission"); });
    }

    private boolean hasElevatedPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (authorization == null) {
            return principal.permissions().stream().map(this::normalize).anyMatch(ELEVATED::contains);
        }
        return authorization.isAllowedAny(principal, "admin", principal.tenantId(),
                List.of("content.manage", "curriculum.manage", "content.lifecycle.manage"));
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
