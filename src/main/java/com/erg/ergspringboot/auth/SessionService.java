package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapPermissions;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUser;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionContextResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionIdentityResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionScopeClasses;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionScopeResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionScopeUnits;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.StudentAccountPolicy;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.EffectivePermissionResolver;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final String DEFAULT_TENANT = "erg";
    private static final int MAX_UNIT_PAGE_SIZE = 100;
    private static final int MAX_CLASS_PAGE_SIZE = 500;

    private final AuthSessionRepository sessionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final SessionUnitScopeResolver unitScopes;

    @Autowired
    public SessionService(
            AuthSessionRepository sessionRepository,
            EducationUnitRepository educationUnitRepository,
            ClassroomRepository classroomRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.unitScopes = new SessionUnitScopeResolver(
                educationUnitRepository, classroomRepository, userRoleAssignmentRepository);
    }

    @Transactional(readOnly = true)
    public SessionContextResponse currentSession(String sessionId, String tenantId) {
        AuthSession session = sessionRepository.findBySessionIdAndTenantId(sessionId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        boolean active = isSessionActive(session);
        User user = session.getUser();
        // Same role sources as currentBootstrap: direct roles plus active role assignments.
        List<String> roles = user == null
                ? List.of()
                : EffectivePermissionResolver.roleNames(user, userRoleAssignmentRepository.findActiveForUser(
                        user.getId(), normalizeTenant(tenantId), OffsetDateTime.now()));
        return new SessionContextResponse(
                session.getSessionId(),
                session.getUserId(),
                session.getTenantId(),
                session.getDeviceId(),
                session.getIpAddress(),
                active,
                roles,
                session.getExpiresAt() == null ? null : session.getExpiresAt().toString(),
                session.getLastActiveAt() == null ? null : session.getLastActiveAt().toString()
        );
    }

    @Transactional(readOnly = true)
    public SessionBootstrapResponse currentBootstrap(String sessionId, String tenantId, UserAccessContext requestedAccess) {
        Identity identity = identity(sessionId, tenantId);
        ScopeCaller caller = identity.caller();
        // A student manages nothing: skip the teacher scope queries entirely.
        List<SessionUnitScopeResolver.ManagedUnit> managedUnits =
                identity.studentOnly() ? List.of() : unitScopes.resolveManageableUnits(caller);
        List<SessionBootstrapClass> manageableClasses = identity.studentOnly() ? List.of()
                : unitScopes.resolveManageableClasses(caller.tenantId(), managedUnits, caller.teacherId());
        SessionBootstrapScope currentScope = unitScopes.resolveCurrentScope(
                requestedAccess, managedUnits, manageableClasses, caller.canAccessGlobalErg());
        return new SessionBootstrapResponse(
                bootstrapUser(identity.user()),
                permissions(identity),
                identity.portals(),
                lifecycle(identity.user(), identity.studentOnly()),
                currentScope,
                unitScopes.toBootstrapUnits(managedUnits),
                manageableClasses,
                identity.session().getTenantId(),
                identity.workspaces()
        );
    }

    /** Identity and permissions only: one session+user query and one role-assignment query, no unit or class queries. */
    @Transactional(readOnly = true)
    public SessionIdentityResponse currentIdentity(String sessionId, String tenantId) {
        return currentIdentity(sessionId, tenantId, null);
    }

    /**
     * With the caller's own (cached) principal for this very session, roles and the permission envelope come from it
     * and only the session+user row is read, so lifecycle flags are always fresh; the principal cache is evicted on every
     * user, role-assignment and override write. Any other principal falls back to the full grant queries.
     */
    @Transactional(readOnly = true)
    public SessionIdentityResponse currentIdentity(String sessionId, String tenantId, ErgPrincipal principal) {
        Identity identity = identity(sessionId, tenantId, principal);
        return new SessionIdentityResponse(
                bootstrapUser(identity.user()),
                identity.session().getTenantId(),
                permissions(identity),
                identity.portals(),
                identity.workspaces(),
                lifecycle(identity.user(), identity.studentOnly())
        );
    }

    /** Query parameters of GET /sessions/scope; sizes are clamped, never trusted. */
    public record ScopeRequest(String unitQuery, int unitPage, int unitSize, String unitId, int classSize) {
    }

    @Transactional(readOnly = true)
    public SessionScopeResponse currentScope(
            String sessionId, String tenantId, UserAccessContext requestedAccess, ScopeRequest request) {
        Identity identity = identity(sessionId, tenantId);
        ScopeCaller caller = identity.caller();
        int page = Math.max(0, request.unitPage());
        int size = Math.clamp(request.unitSize(), 1, MAX_UNIT_PAGE_SIZE);
        if (identity.studentOnly() && (request.unitId() == null || request.unitId().isBlank())) {
            return new SessionScopeResponse(new SessionBootstrapScope("none", null, null),
                    new SessionScopeUnits(List.of(), page, size, 0, false), new SessionScopeClasses(List.of(), false), false);
        }
        SessionScopeUnits units = unitScopes.pageUnits(caller, request.unitQuery(), page, size);
        SessionScopeClasses classes = unitScopes.scopeClasses(
                caller, request.unitId(), Math.clamp(request.classSize(), 1, MAX_CLASS_PAGE_SIZE));
        return new SessionScopeResponse(
                unitScopes.resolveCurrentScopeTargeted(requestedAccess, caller), units, classes, classes.hasNext());
    }

    /** Session, user and grants shared by every session endpoint, so identity and permissions cannot drift apart. */
    private Identity identity(String sessionId, String tenantId) {
        return identity(sessionId, tenantId, null);
    }

    private Identity identity(String sessionId, String tenantId, ErgPrincipal principal) {
        String tenant = normalizeTenant(tenantId);
        AuthSession session = sessionRepository.findWithUserBySessionIdAndTenantId(sessionId, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (!isSessionActive(session)) {
            throw new UnauthorizedException("session is not active");
        }
        User user = session.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("session user not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean fromPrincipal = principal != null && user.getId().equals(principal.userId())
                && sessionId.equals(principal.sessionId()) && tenant.equals(principal.tenantId());
        // Same grant sources as the request principal (ErgPrincipalService) and login (SessionIssuer).
        // The principal path leaves assignments empty: only the identity endpoint uses it, and it never reads them.
        List<UserRoleAssignment> assignments = fromPrincipal ? List.of()
                : userRoleAssignmentRepository.findActiveForUser(user.getId(), tenant, now);
        List<String> roles = fromPrincipal ? principal.roles() : EffectivePermissionResolver.roleNames(user, assignments);
        PermissionEnvelope envelope = fromPrincipal
                ? new PermissionEnvelope(principal.permissions(), principal.deniedPermissions())
                : permissionEnvelope(user, assignments, now);
        List<String> granted = envelope.grantedPermissions();
        boolean studentOnly = StudentAccountPolicy.isStudentOnly(roles, granted, envelope.deniedPermissions());
        boolean canAccessGlobalErg = granted.contains("*");
        // BUG-B-003: Phòng đào tạo and the director manage every school of the organisation; teachers keep the scoped rule.
        boolean allUnits = canAccessGlobalErg
                || roles.stream().anyMatch(role -> "pdt_operator".equalsIgnoreCase(role) || "director".equalsIgnoreCase(role));
        // A bare "*" lists units across tenants only for the verified platform-wide principal (AuthorizationCapabilityPolicy).
        boolean platformWide = WorkspaceCapabilityMapper.isPlatformWide(roles, granted, envelope.deniedPermissions());
        String teacherId = canAccessGlobalErg || granted.contains("admin.workspace.access") ? null : user.getId();
        return new Identity(session, user, roles, envelope, studentOnly, new ScopeCaller(
                user, roles, assignments, canAccessGlobalErg, platformWide, allUnits, tenant, teacherId));
    }

    private SessionBootstrapUser bootstrapUser(User user) {
        return new SessionBootstrapUser(user.getId(), user.getFullName(), user.getEmail(), user.getAvatarUrl());
    }

    private SessionBootstrapPermissions permissions(Identity identity) {
        return new SessionBootstrapPermissions(identity.caller().allUnits(), identity.caller().canAccessGlobalErg(),
                identity.roles(), identity.envelope().grantedPermissions(), identity.envelope().deniedPermissions());
    }

    /** A student account (STU-01) is never sent to onboarding, password change or email verification. */
    private AccountLifecycleResponse lifecycle(User user, boolean studentOnly) {
        List<String> nextSteps = new java.util.ArrayList<>();
        if (!studentOnly) {
            nextSteps.addAll(user.firstLoginSteps());
        }
        String status = nextSteps.isEmpty() ? "ACTIVE" : "FIRST_LOGIN_RESTRICTED";
        return new AccountLifecycleResponse(
                status,
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()),
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                List.copyOf(nextSteps)
        );
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        return email.substring(0, 1) + "***" + email.substring(email.indexOf('@'));
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId.trim();
    }

    private boolean isSessionActive(AuthSession session) {
        User user = session.getUser();
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(OffsetDateTime.now())
                && user != null
                && "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

    private PermissionEnvelope permissionEnvelope(User user, List<UserRoleAssignment> assignments, OffsetDateTime now) {
        Collection<UserPermissionOverride> overrides = user.getPermissionOverrides() == null
                ? userPermissionOverrideRepository.findByUserId(user.getId())
                : user.getPermissionOverrides();
        EffectivePermissionResolver.Result resolved =
                EffectivePermissionResolver.resolve(user.getRoles(), overrides, assignments, now);
        return new PermissionEnvelope(resolved.granted(), resolved.denied());
    }

    private record Identity(
            AuthSession session,
            User user,
            List<String> roles,
            PermissionEnvelope envelope,
            boolean studentOnly,
            ScopeCaller caller
    ) {
        List<String> portals() {
            return WorkspaceCapabilityMapper.legacyPortalsFor(List.of(), roles, envelope.grantedPermissions());
        }

        List<String> workspaces() {
            return WorkspaceCapabilityMapper.canonicalWorkspaces(List.of(), roles, envelope.grantedPermissions());
        }
    }

    private record PermissionEnvelope(List<String> grantedPermissions, List<String> deniedPermissions) {
    }
}
