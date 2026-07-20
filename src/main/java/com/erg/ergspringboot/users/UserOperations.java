package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.*;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.*;
import com.erg.ergspringboot.service.users.UserService;
import org.springframework.stereotype.Service;

/** Compatibility facade for the first users vertical slice. */
@Service
public class UserOperations {
    private final UserService legacy;

    public UserOperations(UserService legacy) {
        this.legacy = legacy;
    }

    public UserResponse current(String userId, String tenantId) {
        return legacy.getCurrentUser(userId, tenantId);
    }

    public UserResponse updateProfile(String userId, String tenantId, UpdateProfileRequest request,
            UserAuditContext auditContext) {
        return legacy.updateProfile(userId, tenantId, request, auditContext.toLegacy());
    }

    public OnboardingResponse completeOnboarding(String userId, String tenantId, OnboardingRequest request) {
        return legacy.completeOnboarding(userId, tenantId, request);
    }
    public PasswordChangeResponse changePassword(String userId, String tenantId, String sessionId,
            ChangePasswordRequest request, UserAuditContext audit) {
        return legacy.changePassword(userId, tenantId, sessionId, request, audit.toLegacy());
    }
    public SessionListResponse listSessions(String userId, String tenantId, String sessionId) {
        return legacy.listSessions(userId, tenantId, sessionId);
    }
    public RevokeSessionResponse revokeSession(String userId, String tenantId, String sessionId) {
        return legacy.revokeSession(userId, tenantId, sessionId);
    }
    public RevokeSessionResponse revokeSession(String userId, String tenantId, String sessionId,
            UserAuditContext audit) {
        return legacy.revokeSession(userId, tenantId, sessionId, audit.toLegacy());
    }
    public UserListResponse listUsers(String tenantId, Integer page, Integer limit, String search, String status, String role) {
        return legacy.listUsers(tenantId, page, limit, search, status, role);
    }
    public UserCreatedResponse createUser(AdminCreateUserRequest request, UserAuditContext audit) {
        return legacy.createUser(request, audit.toLegacy());
    }
    public CredentialDeliveryResponse resetCredential(String userId, String tenantId, String delivery,
            UserAuditContext audit) { return legacy.resetCredential(userId, tenantId, delivery, audit.toLegacy()); }
    public BulkOperationResponse bulkUpdateStatus(String tenantId, BulkStatusRequest request) {
        return legacy.bulkUpdateStatus(tenantId, request);
    }
    public BulkOperationResponse bulkDelete(String tenantId, BulkDeleteRequest request) {
        return legacy.bulkDelete(tenantId, request);
    }
    public UserDetailResponse getUserDetail(String userId, String tenantId) { return legacy.getUserDetail(userId, tenantId); }
    public ActivityListResponse listActivity(String userId, String tenantId, Integer page, Integer limit) {
        return legacy.listActivity(userId, tenantId, page, limit);
    }
    public UserResponse updateUserStatus(String userId, String tenantId, AdminUpdateUserStatusRequest request) {
        return legacy.updateUserStatus(userId, tenantId, request);
    }
    public UserResponse updateUserStatus(String userId, String tenantId, AdminUpdateUserStatusRequest request,
            UserAuditContext audit) { return legacy.updateUserStatus(userId, tenantId, request, audit.toLegacy()); }
    public UserResponse assignRoles(String userId, String tenantId, AdminAssignRolesRequest request,
            UserAuditContext audit) { return legacy.assignRoles(userId, tenantId, request, audit.toLegacy()); }
    public PermissionOverrideResponse assignPermissions(String userId, String tenantId,
            AdminAssignPermissionsRequest request, UserAuditContext audit) {
        return legacy.assignPermissions(userId, tenantId, request, audit.toLegacy());
    }
    public BulkOperationResponse deleteUser(String userId, String tenantId) { return legacy.deleteUser(userId, tenantId); }
}
