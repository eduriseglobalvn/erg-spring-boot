package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.request.users.UserRequests.*;
import com.erg.ergspringboot.common.response.users.UserResponses.*;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.users.UserOperations;
import com.erg.ergspringboot.users.UserOperationsCompatibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link UserOperations}. This bean is a compatibility adapter for
 *             legacy services and tests and must not own user business logic.
 */
@Deprecated
@Service
public class UserService {
    private static final String DEFAULT_TENANT = "erg";
    private final UserOperations operations;

    @Autowired
    public UserService(UserOperations operations) {
        this.operations = operations;
    }

    /** Compatibility constructor retained for legacy tests/callers. */
    @Deprecated
    public UserService(UserRepository users,
            AuthSessionRepository sessions,
            RoleRepository roles,
            PermissionRepository permissions,
            UserPermissionOverrideRepository overrides,
            UserActivityRepository activities,
            PasswordService passwordService,
            UserAccessScopeRepository scopes,
            AccountMailSender mail) {
        this(UserOperationsCompatibility.fromRepositories(
                users, sessions, roles, permissions, overrides, activities,
                passwordService, scopes, mail));
    }

    @Autowired(required = false)
    void setAuthorizationMutationAuditService(AuthorizationMutationAuditService value) {
        UserOperationsCompatibility.setAuthorizationMutationAuditService(operations, value);
    }

    public UserResponse getCurrentUser(String id, String tenant) {
        return operations.current(id, tenant);
    }

    public UserResponse updateProfile(String id, String tenant, UpdateProfileRequest request) {
        return updateProfile(id, tenant, request, null);
    }

    public UserResponse updateProfile(String id, String tenant, UpdateProfileRequest request, AuditContext audit) {
        return operations.updateProfile(id, tenant, request, boundaryAudit(audit));
    }

    public OnboardingResponse completeOnboarding(String id, String tenant, OnboardingRequest request) {
        return operations.completeOnboarding(id, tenant, request);
    }

    public SessionListResponse listSessions(String id, String tenant, String current) {
        return operations.listSessions(id, tenant, current);
    }

    public RevokeSessionResponse revokeSession(String id, String tenant, String session) {
        return revokeSession(id, tenant, session, null);
    }

    public RevokeSessionResponse revokeSession(String id, String tenant, String session, AuditContext audit) {
        return operations.revokeSession(id, tenant, session, boundaryAudit(audit));
    }

    public PasswordChangeResponse changePassword(String id, String tenant, ChangePasswordRequest request) {
        return changePassword(id, tenant, null, request, null);
    }

    public PasswordChangeResponse changePassword(String id, String tenant, String session, ChangePasswordRequest request) {
        return changePassword(id, tenant, session, request, null);
    }

    public PasswordChangeResponse changePassword(String id, String tenant, String session,
            ChangePasswordRequest request, AuditContext audit) {
        return operations.changePassword(id, tenant, session, request, boundaryAudit(audit));
    }

    public UserListResponse listUsers(Integer page, Integer limit, String search, String status, String role) {
        return listUsers(DEFAULT_TENANT, page, limit, search, status, role);
    }

    public UserListResponse listUsers(String tenant, Integer page, Integer limit,
            String search, String status, String role) {
        return operations.listUsers(tenant, page, limit, search, status, role);
    }

    public UserListResponse listUsers(ListUsersQuery query) {
        return listUsers(query.page(), query.limit(), query.search(), query.status(), query.role());
    }

    public UserDetailResponse getUserDetail(String id, String tenant) {
        return operations.getUserDetail(id, tenant);
    }

    public ActivityListResponse listActivity(String id, String tenant, Integer page, Integer limit) {
        return operations.listActivity(id, tenant, page, limit);
    }

    public UserCreatedResponse createUser(AdminCreateUserRequest request) {
        return createUser(request, null);
    }

    public UserCreatedResponse createUser(AdminCreateUserRequest request, AuditContext audit) {
        return operations.createUser(request, boundaryAudit(audit));
    }

    public UserCreatedResponse createUser(AdminCreateUserRequest request, AuditContext audit, ErgPrincipal actor) {
        return operations.createUser(request, boundaryAudit(audit), actor);
    }

    public CredentialDeliveryResponse resetCredential(String id, String tenant, String delivery) {
        return resetCredential(id, tenant, delivery, null);
    }

    public CredentialDeliveryResponse resetCredential(String id, String tenant, String delivery, AuditContext audit) {
        return operations.resetCredential(id, tenant, delivery, boundaryAudit(audit));
    }

    public UserResponse updateUserStatus(String id, String tenant, AdminUpdateUserStatusRequest request) {
        return updateUserStatus(id, tenant, request, null);
    }

    public UserResponse updateUserStatus(String id, String tenant,
            AdminUpdateUserStatusRequest request, AuditContext audit) {
        return operations.updateUserStatus(id, tenant, request, boundaryAudit(audit));
    }

    public BulkOperationResponse deleteUser(String id, String tenant) {
        return operations.deleteUser(id, tenant);
    }

    public BulkOperationResponse bulkUpdateStatus(String tenant, BulkStatusRequest request) {
        return operations.bulkUpdateStatus(tenant, request);
    }

    public BulkOperationResponse bulkDelete(String tenant, BulkDeleteRequest request) {
        return operations.bulkDelete(tenant, request);
    }

    public UserResponse assignRoles(String id, String tenant, AdminAssignRolesRequest request) {
        return assignRoles(id, tenant, request, null);
    }

    public UserResponse assignRoles(String id, String tenant,
            AdminAssignRolesRequest request, AuditContext audit) {
        return operations.assignRoles(id, tenant, request, boundaryAudit(audit));
    }

    public UserResponse assignRoles(String id, String tenant,
            AdminAssignRolesRequest request, AuditContext audit, ErgPrincipal actor) {
        return operations.assignRoles(id, tenant, request, boundaryAudit(audit), actor);
    }

    public PermissionOverrideResponse assignPermissions(String id, String tenant,
            AdminAssignPermissionsRequest request) {
        return assignPermissions(id, tenant, request, null);
    }

    public PermissionOverrideResponse assignPermissions(String id, String tenant,
            AdminAssignPermissionsRequest request, AuditContext audit) {
        return operations.assignPermissions(id, tenant, request, boundaryAudit(audit));
    }

    public record AuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) { }

    private UserAuditContext boundaryAudit(AuditContext audit) {
        return audit == null ? null : new UserAuditContext(
                audit.actorUserId(), audit.requestId(), audit.ipAddress(), audit.userAgent());
    }
}
