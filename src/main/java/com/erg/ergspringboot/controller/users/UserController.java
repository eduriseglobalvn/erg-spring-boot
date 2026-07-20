package com.erg.ergspringboot.controller.users;

import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ChangePasswordRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.RecoveryEmailChallengeRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.RecoveryEmailVerifyRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.CredentialDeliveryRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailChallengeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RecoveryEmailVerificationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PasswordChangeResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.CredentialDeliveryResponse;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.users.UserOperations;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.auth.AuthOperations;
import com.erg.ergspringboot.auth.AuthAuditContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserOperations users;
    private final AuthOperations auth;

    @org.springframework.beans.factory.annotation.Autowired
    public UserController(UserOperations users, AuthOperations auth) {
        this.users = users;
        this.auth = auth;
    }

    /** Compatibility constructor retained for existing focused controller tests. */
    @Deprecated
    UserController(UserOperations users,
            com.erg.ergspringboot.service.auth.AccountVerificationService verification) {
        this(users, new AuthOperations(null, verification));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(HttpServletRequest servletRequest) {
        UserResponse data = users.current(userId(servletRequest), tenantId(servletRequest));
        return ok(data, servletRequest);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        UserResponse data = users.updateProfile(
                userId(servletRequest), tenantId(servletRequest), request, auditContext(servletRequest));
        return ok(data, servletRequest);
    }

    @PostMapping("/me/recovery-email/challenges")
    public ResponseEntity<ApiResponse<RecoveryEmailChallengeResponse>> requestRecoveryEmailChallenge(
            @Valid @RequestBody RecoveryEmailChallengeRequest request,
            HttpServletRequest servletRequest
    ) {
        RecoveryEmailChallengeResponse data = auth.requestRecoveryEmailChallenge(
                userId(servletRequest), tenantId(servletRequest), request,
                new AuthAuditContext(auditContext(servletRequest).actorUserId(), auditContext(servletRequest).requestId(),
                        auditContext(servletRequest).ipAddress(), auditContext(servletRequest).userAgent()));
        return ok(data, servletRequest);
    }

    @PostMapping("/me/recovery-email/verify")
    public ResponseEntity<ApiResponse<RecoveryEmailVerificationResponse>> verifyRecoveryEmailChallenge(
            @Valid @RequestBody RecoveryEmailVerifyRequest request,
            HttpServletRequest servletRequest
    ) {
        RecoveryEmailVerificationResponse data = auth.verifyRecoveryEmail(
                userId(servletRequest), tenantId(servletRequest), request.challengeId(), request.otp(),
                new AuthAuditContext(auditContext(servletRequest).actorUserId(), auditContext(servletRequest).requestId(),
                        auditContext(servletRequest).ipAddress(), auditContext(servletRequest).userAgent()));
        return ok(data, servletRequest);
    }

    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request,
            HttpServletRequest servletRequest
    ) {
        OnboardingResponse data = users.completeOnboarding(userId(servletRequest), tenantId(servletRequest), request);
        return ok(data, servletRequest);
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<PasswordChangeResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        PasswordChangeResponse data = users.changePassword(
                userId(servletRequest), tenantId(servletRequest), sessionId(servletRequest), request,
                auditContext(servletRequest));
        return ok(data, servletRequest);
    }

    @GetMapping("/me/sessions")
    public ResponseEntity<ApiResponse<SessionListResponse>> listSessions(HttpServletRequest servletRequest) {
        SessionListResponse data = users.listSessions(
                userId(servletRequest),
                tenantId(servletRequest),
                sessionId(servletRequest)
        );
        return ok(data, servletRequest);
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<RevokeSessionResponse>> revokeSession(
            @PathVariable String sessionId,
            HttpServletRequest servletRequest
    ) {
        RevokeSessionResponse data = users.revokeSession(userId(servletRequest), tenantId(servletRequest), sessionId);
        return ok(data, servletRequest);
    }

    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<UserListResponse>> listUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            HttpServletRequest servletRequest
    ) {
        UserListResponse data = users.listUsers(tenantId(servletRequest), page, limit, search, status, role);
        return ok(data, servletRequest);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserCreatedResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            HttpServletRequest servletRequest
    ) {
        UserCreatedResponse data = users.createUser(request, auditContext(servletRequest));
        ApiResponse<UserCreatedResponse> response = ApiResponse.success(
                201,
                "Created",
                data,
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.status(201)
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.requestId())
                .body(response);
    }

    @PostMapping("/{userId}/resend-invite")
    public ResponseEntity<ApiResponse<CredentialDeliveryResponse>> resendInvite(
            @PathVariable String userId,
            HttpServletRequest servletRequest
    ) {
        return ok(users.resetCredential(
                userId, tenantId(servletRequest), "EMAIL_INVITE", auditContext(servletRequest)), servletRequest);
    }

    @PostMapping("/{userId}/reset-credential")
    public ResponseEntity<ApiResponse<CredentialDeliveryResponse>> resetCredential(
            @PathVariable String userId,
            @Valid @RequestBody CredentialDeliveryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ok(users.resetCredential(
                userId, tenantId(servletRequest), request.credentialDelivery(), auditContext(servletRequest)), servletRequest);
    }

    @PutMapping("/bulk/status")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkUpdateStatus(
            @Valid @RequestBody BulkStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        BulkOperationResponse data = users.bulkUpdateStatus(tenantId(servletRequest), request);
        return ok(data, servletRequest);
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkDelete(
            @Valid @RequestBody BulkDeleteRequest request,
            HttpServletRequest servletRequest
    ) {
        BulkOperationResponse data = users.bulkDelete(tenantId(servletRequest), request);
        return ok(data, servletRequest);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(
            @PathVariable String userId,
            HttpServletRequest servletRequest
    ) {
        UserDetailResponse data = users.getUserDetail(userId, tenantId(servletRequest));
        return ok(data, servletRequest);
    }

    @GetMapping("/{userId}/activity")
    public ResponseEntity<ApiResponse<ActivityListResponse>> listActivity(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest servletRequest
    ) {
        ActivityListResponse data = users.listActivity(userId, tenantId(servletRequest), page, limit);
        return ok(data, servletRequest);
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        UserResponse data = users.updateUserStatus(userId, tenantId(servletRequest), request);
        return ok(data, servletRequest);
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody AdminAssignRolesRequest request,
            HttpServletRequest servletRequest
    ) {
        UserResponse data = users.assignRoles(
                userId, tenantId(servletRequest), request, auditContext(servletRequest));
        return ok(data, servletRequest);
    }

    @PostMapping("/{userId}/permissions")
    public ResponseEntity<ApiResponse<PermissionOverrideResponse>> assignPermissions(
            @PathVariable String userId,
            @Valid @RequestBody AdminAssignPermissionsRequest request,
            HttpServletRequest servletRequest
    ) {
        PermissionOverrideResponse data = users.assignPermissions(
                userId, tenantId(servletRequest), request, auditContext(servletRequest));
        return ok(data, servletRequest);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> deleteUser(
            @PathVariable String userId,
            HttpServletRequest servletRequest
    ) {
        BulkOperationResponse data = users.deleteUser(userId, tenantId(servletRequest));
        return ok(data, servletRequest);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, HttpServletRequest servletRequest) {
        ApiResponse<T> response = ApiResponse.success(
                200,
                "Success",
                data,
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.ok()
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.requestId())
                .body(response);
    }

    private String userId(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        if (principal != null && principal.userId() != null && !principal.userId().isBlank()) {
            return principal.userId();
        }
        throw new UnauthorizedException("missing authenticated user");
    }

    private String tenantId(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        if (principal != null && principal.tenantId() != null && !principal.tenantId().isBlank()) {
            return principal.tenantId();
        }
        throw new UnauthorizedException("missing authenticated tenant");
    }

    private String sessionId(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-ID");
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.trim();
        }
        ErgPrincipal principal = principal();
        return principal == null ? null : principal.sessionId();
    }

    private ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? null : requestId.toString();
    }

    private UserAuditContext auditContext(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        return new UserAuditContext(
                principal == null ? null : principal.userId(),
                requestId(request),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }
}
