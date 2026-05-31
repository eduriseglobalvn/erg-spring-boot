package com.erg.ergspringboot.controller.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.EffectivePermissionsResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureConfigResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionGroupResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.service.authorization.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> listRoles(HttpServletRequest request) {
        return ok("Roles loaded", authorizationService.listRoles(), request);
    }

    @GetMapping("/roles/{roleId}")
    public ApiResponse<RoleResponse> getRole(@PathVariable String roleId, HttpServletRequest request) {
        return ok("Role loaded", authorizationService.getRole(roleId), request);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest body, HttpServletRequest request) {
        return ApiResponse.success(201, "Role created", authorizationService.createRole(body), request.getRequestURI(), requestId(request));
    }

    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH}, path = "/roles/{roleId}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String roleId,
            @Valid @RequestBody UpdateRoleRequest body,
            HttpServletRequest request
    ) {
        return ok("Role updated", authorizationService.updateRole(roleId, body), request);
    }

    @DeleteMapping("/roles/{roleId}")
    public ApiResponse<Void> deleteRole(@PathVariable String roleId, HttpServletRequest request) {
        authorizationService.deleteRole(roleId);
        return ok("Role deleted", null, request);
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions(HttpServletRequest request) {
        return ok("Permissions loaded", authorizationService.listPermissions(), request);
    }

    @GetMapping("/permission-groups")
    public ApiResponse<List<PermissionGroupResponse>> listPermissionGroups(HttpServletRequest request) {
        return ok("Permission groups loaded", authorizationService.listPermissionGroups(), request);
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ApiResponse<Void> removeRole(
            @PathVariable String userId,
            @PathVariable String roleId,
            HttpServletRequest request
    ) {
        authorizationService.removeRole(userId, roleId);
        return ok("Role removed", null, request);
    }

    @PostMapping("/users/roles/bulk")
    public ApiResponse<BulkOperationResponse> bulkAssignRole(
            @Valid @RequestBody BulkAssignRoleRequest body,
            HttpServletRequest request
    ) {
        return ok("Role assigned", new BulkOperationResponse(authorizationService.bulkAssignRole(body)), request);
    }

    @DeleteMapping("/users/{userId}/permissions/{overrideId}")
    public ApiResponse<Void> deletePermissionOverride(
            @PathVariable String userId,
            @PathVariable String overrideId,
            HttpServletRequest request
    ) {
        authorizationService.deletePermissionOverride(userId, overrideId);
        return ok("Permission override deleted", null, request);
    }

    @GetMapping("/users/{userId}/effective-permissions")
    public ApiResponse<EffectivePermissionsResponse> effectivePermissions(@PathVariable String userId, HttpServletRequest request) {
        return ok("Effective permissions loaded", authorizationService.effectivePermissions(userId), request);
    }

    @GetMapping("/users/{userId}/permission-overrides")
    public ApiResponse<List<PermissionOverrideResponse>> permissionOverrides(@PathVariable String userId, HttpServletRequest request) {
        return ok("Permission overrides loaded", authorizationService.listPermissionOverrides(userId), request);
    }

    @GetMapping("/users/{userId}/feature-config")
    public ApiResponse<FeatureConfigResponse> featureConfig(@PathVariable String userId, HttpServletRequest request) {
        return ok("Feature config loaded", authorizationService.featureConfig(userId), request);
    }

    @GetMapping("/me/feature-config")
    public ApiResponse<FeatureConfigResponse> currentFeatureConfig(HttpServletRequest request) {
        return ok("Feature config loaded", authorizationService.featureConfig(userId(request)), request);
    }

    @PostMapping("/users/{userId}/preview")
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
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }

    private String userId(HttpServletRequest request) {
        String value = request.getHeader("X-User-ID");
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
