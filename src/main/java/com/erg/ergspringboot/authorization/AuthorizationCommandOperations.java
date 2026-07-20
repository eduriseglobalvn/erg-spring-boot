package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.CreateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.EffectivePermissionsResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.FeatureConfigResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionGroupResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionOverrideResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionPageResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PermissionResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.PreviewChangesResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.service.authorization.AuthorizationService;
import com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application boundary for authorization commands and read projections. */
@Service
public class AuthorizationCommandOperations {
    private final AuthorizationService authorization;
    private ScopedRoleAssignmentService scoped;

    public AuthorizationCommandOperations(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Autowired(required = false)
    public void setScopedRoleAssignmentService(ScopedRoleAssignmentService scoped) {
        this.scoped = scoped;
    }

    public List<RoleResponse> listRoles() { return authorization.listRoles(); }
    public RoleResponse getRole(String roleId) { return authorization.getRole(roleId); }
    public RoleResponse createRole(CreateRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        return authorization.createRole(request, actor, context.toLegacy());
    }
    public RoleResponse updateRole(String roleId, UpdateRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        return authorization.updateRole(roleId, request, actor, context.toLegacy());
    }
    public void deleteRole(String roleId, ErgPrincipal actor, AuthorizationAuditContext context) {
        authorization.deleteRole(roleId, actor, context.toLegacy());
    }
    public List<PermissionResponse> listPermissions() { return authorization.listPermissions(); }
    public PermissionPageResponse searchPermissions(Integer page, Integer limit, String search, String group, String portal, String status, Boolean used) {
        return authorization.searchPermissions(page, limit, search, group, portal, status, used);
    }
    public PermissionResponse getPermission(String permissionId) { return authorization.getPermission(permissionId); }
    public List<PermissionGroupResponse> listPermissionGroups() { return authorization.listPermissionGroups(); }
    public void removeRole(String userId, String roleId, String tenantId, ErgPrincipal actor, AuthorizationAuditContext context) {
        authorization.removeRole(userId, roleId, tenantId, actor, context.toLegacy());
    }
    public long bulkAssignRole(BulkAssignRoleRequest request, String tenantId, ErgPrincipal actor, AuthorizationAuditContext context) {
        return authorization.bulkAssignRole(request, tenantId, actor, context.toLegacy());
    }
    public ScopedRoleAssignmentResponse assignScopedRole(String userId, String tenantId, AssignScopedRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        requireScoped();
        return scoped.assign(userId, tenantId, request, actor, context.toLegacy());
    }
    public ScopedRoleAssignmentResponse revokeScopedRole(String userId, String assignmentId, String tenantId, RevokeScopedRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        requireScoped();
        return scoped.revoke(userId, assignmentId, tenantId, request, actor, context.toLegacy());
    }
    public void deletePermissionOverride(String userId, String overrideId, String tenantId, AuthorizationAuditContext context) {
        authorization.deletePermissionOverride(userId, overrideId, tenantId, context.toLegacy());
    }
    public EffectivePermissionsResponse effectivePermissions(String userId) { return authorization.effectivePermissions(userId); }
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId) { return authorization.listPermissionOverrides(userId); }
    public FeatureConfigResponse featureConfig(String userId) { return authorization.featureConfig(userId); }
    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request) { return authorization.previewChanges(userId, request); }

    private void requireScoped() {
        if (scoped == null) throw new IllegalStateException("scoped role assignment service is not configured");
    }
}
