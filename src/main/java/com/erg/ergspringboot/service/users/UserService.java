package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ChangePasswordRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ListUsersQuery;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserItemResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ProvisionedUserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PasswordChangeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.CredentialDeliveryResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ProtectedRolePolicy;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.auth.user.projection.UserListItemProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserRoleNameProjection;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String DEFAULT_TENANT = "erg";

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository permissionOverrideRepository;
    private final UserActivityRepository userActivityRepository;
    private final PasswordService passwordService;
    private final UserAccessScopeRepository userAccessScopeRepository;
    private final AccountMailSender accountMailSender;
    private final UserQueryUseCase userQueryUseCase;
    private final UserProfileUseCase userProfileUseCase;
    private final UserPasswordUseCase userPasswordUseCase;
    private final UserSessionUseCase userSessionUseCase;
    private final UserProvisioningUseCase userProvisioningUseCase;
    private final ProtectedRolePolicy protectedRolePolicy = new ProtectedRolePolicy();
    private AuthorizationMutationAuditService authorizationMutationAuditService;

    public UserService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository permissionOverrideRepository,
            UserActivityRepository userActivityRepository,
            PasswordService passwordService,
            UserAccessScopeRepository userAccessScopeRepository,
            AccountMailSender accountMailSender
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionOverrideRepository = permissionOverrideRepository;
        this.userActivityRepository = userActivityRepository;
        this.passwordService = passwordService;
        this.userAccessScopeRepository = userAccessScopeRepository;
        this.accountMailSender = accountMailSender;
        this.userQueryUseCase = new UserQueryUseCase(userRepository, sessionRepository, userActivityRepository);
        this.userProfileUseCase = new UserProfileUseCase(userRepository, userActivityRepository);
        this.userPasswordUseCase = new UserPasswordUseCase(
                userRepository, sessionRepository, userActivityRepository, passwordService);
        this.userSessionUseCase = new UserSessionUseCase(sessionRepository, userActivityRepository);
        this.userProvisioningUseCase = new UserProvisioningUseCase(userRepository, sessionRepository,
                roleRepository, userAccessScopeRepository, userActivityRepository, passwordService, accountMailSender);
    }

    @Autowired(required = false)
    void setAuthorizationMutationAuditService(
            AuthorizationMutationAuditService authorizationMutationAuditService
    ) {
        this.authorizationMutationAuditService = authorizationMutationAuditService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId, String tenantId) {
        return userQueryUseCase.current(userId, tenantId);
    }

    @Transactional
    public UserResponse updateProfile(String userId, String tenantId, UpdateProfileRequest request) {
        return updateProfile(userId, tenantId, request, null);
    }

    @Transactional
    public UserResponse updateProfile(
            String userId, String tenantId, UpdateProfileRequest request, AuditContext auditContext
    ) {
        return userProfileUseCase.update(userId, tenantId, request, toPublicAudit(auditContext));
    }

    @Transactional
    public OnboardingResponse completeOnboarding(String userId, String tenantId, OnboardingRequest request) {
        return userProfileUseCase.completeOnboarding(userId, tenantId, request);
    }

    @Transactional(readOnly = true)
    public SessionListResponse listSessions(String userId, String tenantId, String currentSessionId) {
        return userSessionUseCase.list(userId, tenantId, currentSessionId);
    }

    @Transactional
    public RevokeSessionResponse revokeSession(String userId, String tenantId, String sessionId) {
        return revokeSession(userId, tenantId, sessionId, null);
    }

    @Transactional
    public RevokeSessionResponse revokeSession(
            String userId, String tenantId, String sessionId, AuditContext auditContext
    ) {
        return userSessionUseCase.revoke(userId, tenantId, sessionId, toPublicAudit(auditContext));
    }

    @Transactional
    public PasswordChangeResponse changePassword(String userId, String tenantId, ChangePasswordRequest request) {
        return changePassword(userId, tenantId, null, request);
    }

    @Transactional
    public PasswordChangeResponse changePassword(
            String userId, String tenantId, String currentSessionId, ChangePasswordRequest request
    ) {
        return changePassword(userId, tenantId, currentSessionId, request, null);
    }

    @Transactional
    public PasswordChangeResponse changePassword(
            String userId, String tenantId, String currentSessionId, ChangePasswordRequest request,
            AuditContext auditContext
    ) {
        return userPasswordUseCase.change(userId, tenantId, currentSessionId, request, toPublicAudit(auditContext));
    }

    @Transactional(readOnly = true)
    public UserListResponse listUsers(Integer page, Integer limit, String search, String status, String role) {
        return listUsers(DEFAULT_TENANT, page, limit, search, status, role);
    }

    @Transactional(readOnly = true)
    public UserListResponse listUsers(
            String tenantId, Integer page, Integer limit, String search, String status, String role
    ) {
        return userQueryUseCase.list(tenantId, page, limit, search, status, role);
    }

    public UserListResponse listUsers(ListUsersQuery query) {
        return listUsers(query.page(), query.limit(), query.search(), query.status(), query.role());
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(String userId, String tenantId) {
        return userQueryUseCase.detail(userId, tenantId);
    }

    @Transactional(readOnly = true)
    public ActivityListResponse listActivity(String userId, String tenantId, Integer page, Integer limit) {
        return userQueryUseCase.activity(userId, tenantId, page, limit);
    }

    @Transactional
    public UserCreatedResponse createUser(AdminCreateUserRequest request) {
        return createUser(request, null);
    }

    @Transactional
    public UserCreatedResponse createUser(AdminCreateUserRequest request, AuditContext auditContext) {
        return createUser(request, auditContext, SecurityUtils.principal());
    }

    @Transactional
    public UserCreatedResponse createUser(
            AdminCreateUserRequest request, AuditContext auditContext, ErgPrincipal actor
    ) {
        return userProvisioningUseCase.create(request, toPublicAudit(auditContext), actor);
    }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new java.util.ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
        String lifecycleStatus = nextSteps.isEmpty()
                ? "ACTIVE"
                : ("INVITED".equalsIgnoreCase(user.getStatus()) ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        return new AccountLifecycleResponse(
                lifecycleStatus,
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()),
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                List.copyOf(nextSteps)
        );
    }

    @Transactional
    public CredentialDeliveryResponse resetCredential(
            String userId, String tenantId, String requestedDelivery
    ) {
        return resetCredential(userId, tenantId, requestedDelivery, null);
    }

    @Transactional
    public CredentialDeliveryResponse resetCredential(
            String userId, String tenantId, String requestedDelivery, AuditContext auditContext
    ) {
        return userProvisioningUseCase.reset(userId, tenantId, requestedDelivery, toPublicAudit(auditContext));
    }

    private String normalizeOptionalEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return null;
        int separator = email.indexOf('@');
        return email.substring(0, 1) + "***" + email.substring(separator);
    }

    @Transactional
    public UserResponse updateUserStatus(String userId, String tenantId, AdminUpdateUserStatusRequest request) {
        return updateUserStatus(userId, tenantId, request, null);
    }

    @Transactional
    public UserResponse updateUserStatus(
            String userId, String tenantId, AdminUpdateUserStatusRequest request, AuditContext auditContext
    ) {
        String status = normalizeStatus(request.status());
        User user = findUser(userId, tenantId);
        String previousStatus = safeAuditValue(user.getStatus());
        user.setStatus(status);
        userRepository.save(user);
        revokeActiveSessionsIfLocked(user, normalizeTenant(tenantId), status);
        recordActivity(user, auditContext, "EMPLOYEE_STATUS_CHANGED", "Employee account status changed",
                Map.of("status", previousStatus), Map.of("status", status));
        return toResponse(user);
    }

    @Transactional
    public BulkOperationResponse deleteUser(String userId, String tenantId) {
        User user = findUser(userId, tenantId);
        userRepository.delete(user);
        return new BulkOperationResponse(1);
    }

    @Transactional
    public BulkOperationResponse bulkUpdateStatus(String tenantId, BulkStatusRequest request) {
        String status = normalizeStatus(request.status());
        List<User> users = findUsers(request.userIds(), tenantId);
        users.forEach(user -> user.setStatus(status));
        userRepository.saveAll(users);
        String normalizedTenant = normalizeTenant(tenantId);
        users.forEach(user -> revokeActiveSessionsIfLocked(user, normalizedTenant, status));
        return new BulkOperationResponse(users.size());
    }

    @Transactional
    public BulkOperationResponse bulkDelete(String tenantId, BulkDeleteRequest request) {
        List<User> users = findUsers(request.userIds(), tenantId);
        userRepository.deleteAll(users);
        return new BulkOperationResponse(users.size());
    }

    @Transactional
    public UserResponse assignRoles(String userId, String tenantId, AdminAssignRolesRequest request) {
        return assignRoles(userId, tenantId, request, null);
    }

    @Transactional
    public UserResponse assignRoles(
            String userId, String tenantId, AdminAssignRolesRequest request, AuditContext auditContext
    ) {
        return assignRoles(userId, tenantId, request, auditContext, SecurityUtils.principal());
    }

    @Transactional
    public UserResponse assignRoles(
            String userId,
            String tenantId,
            AdminAssignRolesRequest request,
            AuditContext auditContext,
            ErgPrincipal actor
    ) {
        User user = findUser(userId, tenantId);
        List<String> previousRoles = roles(user.getRoles());
        List<String> roleNames = request.roles().stream()
                .map(this::trimToNull)
                .filter(name -> name != null)
                .toList();
        List<Role> roles = roleRepository.findByNameInIgnoreCase(roleNames);
        if (roles.size() != new LinkedHashSet<>(roleNames).size()) {
            throw new BadRequestException("one or more roles were not found");
        }
        protectedRolePolicy.assertCanGrant(actor, roles);
        user.setRoles(new LinkedHashSet<>(roles));
        userRepository.save(user);
        recordActivity(user, auditContext, "USER_ROLES_CHANGED", "User role groups changed",
                Map.of("roles", previousRoles), Map.of("roles", roles(user.getRoles())));
        authorizationMutationAudit().authorizationChanged(
                List.of(new UserRef(user.getId(), normalizeTenant(tenantId))),
                roles.stream().map(Role::getId).sorted().collect(Collectors.joining(",")),
                "USER_ROLES_CHANGED",
                mutationContext(user, auditContext));
        return toResponse(user);
    }

    @Transactional
    public PermissionOverrideResponse assignPermissions(String userId, String tenantId, AdminAssignPermissionsRequest request) {
        return assignPermissions(userId, tenantId, request, null);
    }

    @Transactional
    public PermissionOverrideResponse assignPermissions(
            String userId, String tenantId, AdminAssignPermissionsRequest request, AuditContext auditContext
    ) {
        User user = findUser(userId, tenantId);
        String grantType = normalizeGrantType(request.grantType());
        List<String> permissionNames = request.permissions().stream()
                .map(this::trimToNull)
                .filter(name -> name != null)
                .toList();
        List<Permission> permissions = permissionRepository.findByNameInIgnoreCase(permissionNames);
        if (permissions.size() != new LinkedHashSet<>(permissionNames).size()) {
            throw new BadRequestException("one or more permissions were not found");
        }
        permissionOverrideRepository.deleteByUserId(userId);
        List<UserPermissionOverride> overrides = permissions.stream()
                .map(permission -> {
                    UserPermissionOverride override = new UserPermissionOverride();
                    override.setId(IdGenerator.newId());
                    override.setUserId(userId);
                    override.setPermission(permission.getName());
                    override.setGrantType(grantType);
                    override.setReason(trimToNull(request.reason()));
                    override.setExpiresAt(parseOptionalTimestamp(request.expiresAt()));
                    override.setCreatedAt(now());
                    return override;
                })
                .toList();
        permissionOverrideRepository.saveAll(overrides);
        recordActivity(user, auditContext, "USER_PERMISSIONS_CHANGED", "User permission overrides changed",
                Map.of(), Map.of("grantType", grantType, "permissions", permissionNames));
        authorizationMutationAudit().authorizationChanged(
                List.of(new UserRef(user.getId(), normalizeTenant(tenantId))),
                permissions.stream().map(Permission::getId).sorted().collect(Collectors.joining(",")),
                "USER_PERMISSIONS_CHANGED",
                mutationContext(user, auditContext));
        return new PermissionOverrideResponse(userId, permissions.stream().map(Permission::getName).toList(), grantType);
    }

    private AuthorizationMutationAuditService authorizationMutationAudit() {
        if (authorizationMutationAuditService == null) {
            throw new IllegalStateException("authorization mutation audit service is not configured");
        }
        return authorizationMutationAuditService;
    }

    private AuditContext mutationContext(User target, AuditContext context) {
        return context == null ? new AuditContext(target.getId(), null, null, null) : context;
    }

    private User findUser(String userId, String tenantId) {
        return userRepository.findByIdAndTenantId(userId, normalizeTenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    private void recordActivity(
            User target,
            AuditContext auditContext,
            String action,
            String description,
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        AuditContext context = auditContext == null
                ? new AuditContext(target.getId(), null, null, null)
                : auditContext;
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(isBlank(context.actorUserId()) ? target.getId() : context.actorUserId());
        activity.setTenantId(normalizeTenant(target.getTenantId()));
        activity.setAction(action);
        activity.setTargetUserId(target.getId());
        activity.setIpAddress(trimToNull(context.ipAddress()));
        activity.setUserAgent(trimToNull(context.userAgent()));
        activity.setDescription(description);
        activity.setRequestId(trimToNull(context.requestId()));
        activity.setMetadata(Map.of("before", before, "after", after));
        activity.setCreatedAt(now());
        userActivityRepository.save(activity);
    }

    private Map<String, Object> profileAuditState(User user) {
        return Map.of(
                "fullName", safeAuditValue(user.getFullName()),
                "phone", safeAuditValue(user.getPhone()),
                "isProfileCompleted", Boolean.TRUE.equals(user.getIsProfileCompleted())
        );
    }

    private String safeAuditValue(String value) {
        return value == null ? "" : value;
    }

    public record AuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) {
    }

    private List<User> findUsers(List<String> userIds, String tenantId) {
        return userIds.stream()
                .map(userId -> findUser(userId, tenantId))
                .toList();
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!List.of("ACTIVE", "PENDING", "BLOCKED", "BANNED", "INACTIVE").contains(normalized)) {
            throw new BadRequestException("invalid user status");
        }
        return normalized;
    }

    private void revokeActiveSessionsIfLocked(User user, String tenantId, String status) {
        if (!List.of("BLOCKED", "BANNED", "INACTIVE").contains(status)) {
            return;
        }
        List<AuthSession> sessions = sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), tenantId);
        if (sessions.isEmpty()) {
            return;
        }
        OffsetDateTime revokedAt = now();
        String reason = "status_changed_" + status.toLowerCase();
        sessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        });
        sessionRepository.saveAll(sessions);
    }

    private String normalizeGrantType(String grantType) {
        String normalized = grantType.trim().toUpperCase();
        if (!List.of("GRANT", "DENY").contains(normalized)) {
            throw new BadRequestException("invalid permission grant type");
        }
        return normalized;
    }

    private UserAuditContext toPublicAudit(AuditContext audit) {
        return audit == null ? null
                : new UserAuditContext(audit.actorUserId(), audit.requestId(), audit.ipAddress(), audit.userAgent());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getAddress(),
                user.getCity(),
                user.getDistrict(),
                user.getJobTitle(),
                user.getRegion(),
                user.getStatus(),
                user.getProvider(),
                user.getAccountType(),
                user.getLastLoginProvider(),
                roles(user.getRoles()),
                user.getSocialLinks() == null ? Map.of() : user.getSocialLinks(),
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                toIso(user.getLastLoginAt()),
                user.getLoginCount(),
                user.getTenantId(),
                toIso(user.getCreatedAt()),
                toIso(user.getUpdatedAt()),
                lifecycle(user)
        );
    }

    private UserItemResponse toItemResponse(User user) {
        return new UserItemResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getStatus(),
                user.getProvider(),
                user.getAccountType(),
                user.getLastLoginProvider(),
                roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                toIso(user.getLastLoginAt()),
                toIso(user.getCreatedAt())
        );
    }

    private UserItemResponse toItemResponse(UserListItemProjection user, List<String> roles) {
        return new UserItemResponse(
                user.id(),
                user.email(),
                user.fullName(),
                user.avatarUrl(),
                user.phone(),
                user.status(),
                user.provider(),
                user.accountType(),
                user.lastLoginProvider(),
                roleNames(roles),
                Boolean.TRUE.equals(user.isProfileCompleted()),
                toIso(user.lastLoginAt()),
                toIso(user.createdAt())
        );
    }

    private SessionResponse toSessionResponse(AuthSession session, String currentSessionId) {
        OffsetDateTime lastSeenAt = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
        return new SessionResponse(
                session.getSessionId(),
                session.getDeviceId(),
                session.getDeviceName(),
                session.getPortal(),
                session.getIpAddress(),
                session.getUserAgent(),
                deviceType(session.getUserAgent()),
                session.getSessionId() != null && session.getSessionId().equals(currentSessionId),
                session.getRevokedAt() != null,
                session.getRevokedReason(),
                toIso(session.getExpiresAt()),
                toIso(session.getCreatedAt()),
                toIso(lastSeenAt)
        );
    }

    private ActivityResponse toActivityResponse(UserActivity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getUserId(),
                activity.getAction(),
                activity.getTargetUserId(),
                activity.getIpAddress(),
                activity.getDescription(),
                activity.getMetadata() == null ? Map.of() : activity.getMetadata(),
                toIso(activity.getCreatedAt())
        );
    }

    private String deviceType(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        if (ua.contains("ipad")) {
            return "tablet";
        }
        if (ua.contains("curl") || ua.contains("http")) {
            return "api";
        }
        return "desktop";
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream()
                .map(Role::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }

    private List<String> roleNames(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }

    private boolean isProfileCompleted(User user) {
        return !isBlank(user.getFullName()) && !isBlank(user.getPhone());
    }

    private String normalizeTenant(String tenantId) {
        return isBlank(tenantId) ? DEFAULT_TENANT : tenantId.trim();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime parseOptionalTimestamp(String value) {
        return isBlank(value) ? null : OffsetDateTime.parse(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

