package com.erg.ergspringboot.controller.users;

import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeDetailResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeListResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.CreateEmployeeRequest;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeCreatedResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.RevokeEmployeeSessionRequest;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityEventPageResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.SecurityFeedPageResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.UpdateEmployeeRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.CredentialDeliveryRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.CredentialDeliveryResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.users.EmployeeOperations;
import com.erg.ergspringboot.users.UserOperations;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.authorization.authorization.UsersManage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminEmployeeController {

    private final UserOperations users;
    private final EmployeeOperations employees;

    public AdminEmployeeController(
            UserOperations users,
            EmployeeOperations employees
    ) {
        this.users = users;
        this.employees = employees;
    }

    @GetMapping({"", "/"})
    @UsersManage
    public ResponseEntity<ApiResponse<EmployeeListResponse>> listUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            HttpServletRequest request
    ) {
        return ok(employees.listEmployees(
                tenantId(), page, limit, search, status, role), request);
    }

    @GetMapping("/security-feed")
    @UsersManage
    public ResponseEntity<ApiResponse<SecurityFeedPageResponse>> securityFeed(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        return ok(employees.listUnifiedFeed(
                tenantId(), userId, sourceType, page, limit), request);
    }

    @PostMapping
    @UsersManage
    public ResponseEntity<ApiResponse<EmployeeCreatedResponse>> createUser(
            @Valid @RequestBody CreateEmployeeRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                201, "Created", employees.createEmployee(
                        tenantId(), body, auditContext(request)),
                request.getRequestURI(), requestId(request)));
    }

    @GetMapping("/{userId}")
    @UsersManage
    public ResponseEntity<ApiResponse<EmployeeDetailResponse>> getEmployee(
            @PathVariable String userId,
            HttpServletRequest request
    ) {
        return ok(employees.getEmployee(userId, tenantId()), request);
    }

    @PatchMapping("/{userId}")
    @UsersManage
    public ResponseEntity<ApiResponse<EmployeeDetailResponse>> updateEmployee(
            @PathVariable String userId,
            @Valid @RequestBody UpdateEmployeeRequest body,
            HttpServletRequest request
    ) {
        return ok(employees.updateEmployee(userId, tenantId(), body, auditContext(request)), request);
    }

    @PutMapping("/{userId}/status")
    @UsersManage
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest body,
            HttpServletRequest request
    ) {
        return ok(users.updateUserStatus(userId, tenantId(), body, auditContext(request)), request);
    }

    @PostMapping("/{userId}/roles")
    @UsersManage
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable String userId,
            @Valid @RequestBody AdminAssignRolesRequest body,
            HttpServletRequest request
    ) {
        return ok(users.assignRoles(userId, tenantId(), body, auditContext(request)), request);
    }

    @PostMapping("/{userId}/reset-credential")
    @UsersManage
    public ResponseEntity<ApiResponse<CredentialDeliveryResponse>> resetCredential(
            @PathVariable String userId,
            @Valid @RequestBody CredentialDeliveryRequest body,
            HttpServletRequest request
    ) {
        return ok(users.resetCredential(userId, tenantId(), body.credentialDelivery(), auditContext(request)), request);
    }

    @GetMapping("/{userId}/activity")
    @UsersManage
    public ResponseEntity<ApiResponse<ActivityListResponse>> activity(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        return ok(users.listActivity(userId, tenantId(), page, limit), request);
    }

    @GetMapping("/{userId}/security-events")
    @UsersManage
    public ResponseEntity<ApiResponse<SecurityEventPageResponse>> securityEvents(
            @PathVariable String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        return ok(employees.listSecurityEvents(userId, tenantId(), page, limit), request);
    }

    @GetMapping("/{userId}/sessions")
    @UsersManage
    public ResponseEntity<ApiResponse<SessionListResponse>> sessions(
            @PathVariable String userId,
            HttpServletRequest request
    ) {
        return ok(users.listSessions(userId, tenantId(), null), request);
    }

    @PostMapping("/{userId}/sessions/revoke")
    @UsersManage
    public ResponseEntity<ApiResponse<RevokeSessionResponse>> revokeSession(
            @PathVariable String userId,
            @Valid @RequestBody RevokeEmployeeSessionRequest body,
            HttpServletRequest request
    ) {
        return ok(users.revokeSession(userId, tenantId(), body.sessionId(), auditContext(request)), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, "Success", data, request.getRequestURI(), requestId(request)));
    }

    private String tenantId() {
        ErgPrincipal principal = principal();
        if (principal == null || principal.tenantId() == null || principal.tenantId().isBlank()) {
            throw new UnauthorizedException("missing authenticated tenant");
        }
        return principal.tenantId();
    }

    private ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal ? principal : null;
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }

    private UserAuditContext auditContext(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        return new UserAuditContext(
                principal == null ? null : principal.userId(), requestId(request),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
