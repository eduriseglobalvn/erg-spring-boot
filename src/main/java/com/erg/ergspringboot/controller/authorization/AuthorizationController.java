package com.erg.ergspringboot.controller.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.EffectivePermissionsResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureConfigResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionGroupResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionPageResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.authorization.AuthorizationAuditContext;
import com.erg.ergspringboot.authorization.AuthorizationCommandOperations;
import com.erg.ergspringboot.authorization.authorization.RbacPermissionRead;
import com.erg.ergspringboot.authorization.authorization.RbacRoleAssign;
import com.erg.ergspringboot.authorization.authorization.RbacRoleCreate;
import com.erg.ergspringboot.authorization.authorization.RbacRoleDelete;
import com.erg.ergspringboot.authorization.authorization.RbacRoleRead;
import com.erg.ergspringboot.authorization.authorization.RbacRoleUpdate;
import com.erg.ergspringboot.exception.UnauthorizedException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

    private final AuthorizationCommandOperations authorizationService;

    @Autowired
    public AuthorizationController(AuthorizationCommandOperations authorizationService) {
        this.authorizationService = authorizationService;
    }

    /** Compatibility constructor retained for focused controller tests and old embedders. */
    @Deprecated
    public AuthorizationController(com.erg.ergspringboot.service.authorization.AuthorizationService legacy) {
        this.authorizationService = new AuthorizationCommandOperations(legacy);
    }

    /** Compatibility injection seam; production wiring uses the facade bean. */
    @Deprecated
    public void setScopedRoleAssignmentService(com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService scoped) {
        authorizationService.setScopedRoleAssignmentService(scoped);
    }

    @GetMapping("/roles")
    @RbacRoleRead
    public ApiResponse<List<RoleResponse>> listRoles(HttpServletRequest request) {
        return ok("Roles loaded", authorizationService.listRoles(), request);
    }

    @GetMapping("/roles/{roleId}")
    @RbacRoleRead
    public ApiResponse<RoleResponse> getRole(@PathVariable String roleId, HttpServletRequest request) {
        return ok("Role loaded", authorizationService.getRole(roleId), request);
    }

    @PostMapping("/roles")
    @RbacRoleCreate
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest body, HttpServletRequest request) {
        ErgPrincipal principal = principal();
        return ApiResponse.success(
                201,
                "Role created",
                authorizationService.createRole(body, principal, auditContext(request)),
                request.getRequestURI(),
                requestId(request));
    }

    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH}, path = "/roles/{roleId}")
    @RbacRoleUpdate
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String roleId,
            @Valid @RequestBody UpdateRoleRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        return ok(
                "Role updated",
                authorizationService.updateRole(roleId, body, principal, auditContext(request)),
                request);
    }

    @DeleteMapping("/roles/{roleId}")
    @RbacRoleDelete
    public ApiResponse<Void> deleteRole(@PathVariable String roleId, HttpServletRequest request) {
        ErgPrincipal principal = principal();
        authorizationService.deleteRole(roleId, principal, auditContext(request));
        return ok("Role deleted", null, request);
    }

    @GetMapping("/permissions")
    @RbacPermissionRead
    public ApiResponse<List<PermissionResponse>> listPermissions(HttpServletRequest request) {
        return ok("Permissions loaded", authorizationService.listPermissions(), request);
    }

    @GetMapping("/permissions/catalog")
    @RbacPermissionRead
    public ApiResponse<PermissionPageResponse> permissionCatalog(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String portal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean used,
            HttpServletRequest request
    ) {
        return ok(
                "Permission catalog loaded",
                authorizationService.searchPermissions(page, limit, search, group, portal, status, used),
                request
        );
    }

    @GetMapping("/permissions/{permissionId}")
    @RbacPermissionRead
    public ApiResponse<PermissionResponse> permissionDetail(
            @PathVariable String permissionId,
            HttpServletRequest request
    ) {
        return ok("Permission loaded", authorizationService.getPermission(permissionId), request);
    }

    @GetMapping("/permission-groups")
    @RbacPermissionRead
    public ApiResponse<List<PermissionGroupResponse>> listPermissionGroups(HttpServletRequest request) {
        return ok("Permission groups loaded", authorizationService.listPermissionGroups(), request);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @RbacRoleAssign
    public ApiResponse<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String roleId,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        authorizationService.removeRole(
                userId, roleId, principal.tenantId(), principal, auditContext(request));
        return ok("Role removed", null, request);
    }

    @PostMapping("/users/roles/bulk")
    @RbacRoleAssign
    public ApiResponse<BulkOperationResponse> bulkAssignRole(
            @Valid @RequestBody BulkAssignRoleRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        return ok(
                "Role assigned",
                new BulkOperationResponse(authorizationService.bulkAssignRole(
                        body, principal.tenantId(), principal, auditContext(request))),
                request);
    }

    @PostMapping("/users/{userId}/role-assignments")
    @RbacRoleAssign
    public ApiResponse<ScopedRoleAssignmentResponse> assignScopedRole(
            @PathVariable String userId,
            @Valid @RequestBody AssignScopedRoleRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        return ok(
                "Scoped role assigned",
                authorizationService.assignScopedRole(userId, principal.tenantId(), body, principal, auditContext(request)),
                request
        );
    }

    @DeleteMapping("/users/{userId}/role-assignments/{assignmentId}")
    @RbacRoleAssign
    public ApiResponse<ScopedRoleAssignmentResponse> revokeScopedRole(
            @PathVariable String userId,
            @PathVariable String assignmentId,
            @RequestBody(required = false) RevokeScopedRoleRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        return ok(
                "Scoped role revoked",
                authorizationService.revokeScopedRole(
                        userId, assignmentId, principal.tenantId(), body, principal, auditContext(request)),
                request
        );
    }

    @DeleteMapping("/users/{userId}/permissions/{overrideId}")
    @RbacRoleAssign
    public ApiResponse<Void> deletePermissionOverride(
            @PathVariable String userId,
            @PathVariable String overrideId,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = principal();
        authorizationService.deletePermissionOverride(
                userId, overrideId, principal.tenantId(), auditContext(request));
        return ok("Permission override deleted", null, request);
    }

    @GetMapping("/users/{userId}/effective-permissions")
    @RbacPermissionRead
    public ApiResponse<EffectivePermissionsResponse> effectivePermissions(@PathVariable String userId, HttpServletRequest request) {
        return ok("Effective permissions loaded", authorizationService.effectivePermissions(userId), request);
    }

    @GetMapping("/users/{userId}/permission-overrides")
    @RbacPermissionRead
    public ApiResponse<List<PermissionOverrideResponse>> permissionOverrides(@PathVariable String userId, HttpServletRequest request) {
        return ok("Permission overrides loaded", authorizationService.listPermissionOverrides(userId), request);
    }

    @GetMapping("/users/{userId}/feature-config")
    @RbacPermissionRead
    public ApiResponse<FeatureConfigResponse> featureConfig(@PathVariable String userId, HttpServletRequest request) {
        return ok("Feature config loaded", authorizationService.featureConfig(userId), request);
    }

    @GetMapping("/me/feature-config")
    @RbacPermissionRead
    public ApiResponse<FeatureConfigResponse> currentFeatureConfig(HttpServletRequest request) {
        return ok("Feature config loaded", authorizationService.featureConfig(userId(request)), request);
    }

    @PostMapping("/users/{userId}/preview")
    @RbacPermissionRead
    public ApiResponse<PreviewChangesResponse> previewChanges(
            @PathVariable String userId,
            @RequestBody(required = false) PreviewChangesRequest body,
            HttpServletRequest request
    ) {
        return ok("Permission changes previewed", authorizationService.previewChanges(userId, body == null ? new PreviewChangesRequest(java.util.Map.of()) : body), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }

    private String userId(HttpServletRequest request) {
        return principal().userId();
    }

    private AuthorizationAuditContext auditContext(HttpServletRequest request) {
        ErgPrincipal principal = principal();
        return new AuthorizationAuditContext(
                principal.userId(),
                requestId(request),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    private ErgPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErgPrincipal principal
                && principal.userId() != null && !principal.userId().isBlank()
                && principal.tenantId() != null && !principal.tenantId().isBlank()) {
            return principal;
        }
        throw new UnauthorizedException("missing authenticated principal");
    }
}
