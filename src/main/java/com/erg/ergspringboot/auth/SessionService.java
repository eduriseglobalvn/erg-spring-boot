package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapClass;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapPermissions;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUnit;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapUser;
import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionContextResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
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
        List<String> roles = session.getUser() == null || session.getUser().getRoles() == null
                ? List.of()
                : session.getUser().getRoles().stream().map(Role::getName).toList();
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
        AuthSession session = sessionRepository.findWithUserBySessionIdAndTenantId(sessionId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (!isSessionActive(session)) {
            throw new UnauthorizedException("session is not active");
        }
        User user = session.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("session user not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        // Same grant sources as the request principal (ErgPrincipalService) and login (SessionIssuer).
        List<UserRoleAssignment> assignments =
                userRoleAssignmentRepository.findActiveForUser(user.getId(), normalizeTenant(tenantId), now);
        List<String> roles = EffectivePermissionResolver.roleNames(user, assignments);
        PermissionEnvelope permissionEnvelope = permissionEnvelope(user, assignments, now);
        boolean studentOnly = StudentAccountPolicy.isStudentOnly(
                roles, permissionEnvelope.grantedPermissions(), permissionEnvelope.deniedPermissions());
        boolean canAccessGlobalErg = permissionEnvelope.grantedPermissions().contains("*");

        List<SessionUnitScopeResolver.ManagedUnit> managedUnits = unitScopes.resolveManageableUnits(user, roles, canAccessGlobalErg, normalizeTenant(tenantId));
        List<SessionBootstrapUnit> manageableUnits = unitScopes.toBootstrapUnits(managedUnits);
        List<SessionBootstrapClass> manageableClasses = unitScopes.resolveManageableClasses(normalizeTenant(tenantId), managedUnits);

        SessionBootstrapScope currentScope = unitScopes.resolveCurrentScope(
                requestedAccess,
                managedUnits,
                manageableClasses,
                canAccessGlobalErg
        );

        return new SessionBootstrapResponse(
                new SessionBootstrapUser(user.getId(), user.getFullName(), user.getEmail(), user.getAvatarUrl()),
                new SessionBootstrapPermissions(
                        canAccessGlobalErg,
                        roles,
                        permissionEnvelope.grantedPermissions(),
                        permissionEnvelope.deniedPermissions()
                ),
                effectivePortals(roles, permissionEnvelope.grantedPermissions()),
                lifecycle(user, studentOnly),
                currentScope,
                manageableUnits,
                manageableClasses,
                session.getTenantId(),
                WorkspaceCapabilityMapper.canonicalWorkspaces(List.of(), roles, permissionEnvelope.grantedPermissions())
        );
    }

    /** A student account (STU-01) is never sent to onboarding, password change or email verification. */
    private AccountLifecycleResponse lifecycle(User user, boolean studentOnly) {
        List<String> nextSteps = new java.util.ArrayList<>();
        if (!studentOnly) {
            if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
            if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
            if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
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
    private List<String> effectivePortals(List<String> roles, List<String> grantedPermissions) {
        return WorkspaceCapabilityMapper.legacyPortalsFor(List.of(), roles, grantedPermissions);
    }

    private record PermissionEnvelope(List<String> grantedPermissions, List<String> deniedPermissions) {
    }
}
