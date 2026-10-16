package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.catalog.AuthorizationPermissionCatalogUseCase;
import com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
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
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.UpdateRoleRequest;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.service.authorization.AuthorizationService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public authorization boundary; production wiring injects module use cases directly. */
@Service
public class AuthorizationCommandOperations {
    private final AuthorizationPermissionCatalogUseCase catalog;
    private final AuthorizationRoleDefinitionUseCase roles;
    private final AuthorizationAccessQueryUseCase access;
    private final AuthorizationDirectRoleUseCase directRoles;
    private final AuthorizationPermissionOverrideUseCase overrides;
    private final AuthorizationService legacy;
    private ScopedRoleAssignmentService scoped;

    @Autowired
    public AuthorizationCommandOperations(
            AuthorizationPermissionCatalogUseCase catalog,
            AuthorizationRoleDefinitionUseCase roles,
            AuthorizationAccessQueryUseCase access,
            AuthorizationDirectRoleUseCase directRoles,
            AuthorizationPermissionOverrideUseCase overrides) {
        this.catalog = catalog;
        this.roles = roles;
        this.access = access;
        this.directRoles = directRoles;
        this.overrides = overrides;
        this.legacy = null;
    }

    /** Compatibility constructor for controller tests and old embedders. */
    @Deprecated
    public AuthorizationCommandOperations(AuthorizationService legacy) {
        this.catalog = null;
        this.roles = null;
        this.access = null;
        this.directRoles = null;
        this.overrides = null;
        this.legacy = legacy;
    }

    @Autowired(required = false)
    public void setScopedRoleAssignmentService(ScopedRoleAssignmentService scoped) {
        this.scoped = scoped;
    }

    public List<RoleResponse> listRoles() {
        return legacy != null ? legacy.listRoles() : roles.list();
    }

    public RoleResponse getRole(String roleId) {
        return legacy != null ? legacy.getRole(roleId) : roles.get(roleId);
    }

    public RoleResponse createRole(CreateRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        return legacy != null ? legacy.createRole(request, actor, context.toLegacy())
                : roles.create(request, actor, context.toLegacy());
    }

    public RoleResponse updateRole(String roleId, UpdateRoleRequest request, ErgPrincipal actor,
            AuthorizationAuditContext context) {
        return legacy != null ? legacy.updateRole(roleId, request, actor, context.toLegacy())
                : roles.update(roleId, request, actor, context.toLegacy());
    }

    public void deleteRole(String roleId, ErgPrincipal actor, AuthorizationAuditContext context) {
        if (legacy != null) legacy.deleteRole(roleId, actor, context.toLegacy());
        else roles.delete(roleId, actor, context.toLegacy());
    }

    public List<PermissionResponse> listPermissions() {
        return legacy != null ? legacy.listPermissions() : catalog.list();
    }

    public PermissionPageResponse searchPermissions(Integer page, Integer limit, String search, String group,
            String portal, String status, Boolean used) {
        return legacy != null ? legacy.searchPermissions(page, limit, search, group, portal, status, used)
                : catalog.search(page, limit, search, group, portal, status, used);
    }

    public PermissionResponse getPermission(String permissionId) {
        return legacy != null ? legacy.getPermission(permissionId) : catalog.get(permissionId);
    }

    public List<PermissionGroupResponse> listPermissionGroups() {
        return legacy != null ? legacy.listPermissionGroups() : catalog.groups();
    }

    public void removeRole(String userId, String roleId, String tenantId, ErgPrincipal actor,
            AuthorizationAuditContext context) {
        if (legacy != null) legacy.removeRole(userId, roleId, tenantId, actor, context.toLegacy());
        else directRoles.remove(userId, roleId, tenantId, actor, context.toLegacy());
    }

    public RoleResponse assignRoles(String userId, String tenantId, List<String> roleIds,
            ErgPrincipal actor, AuthorizationAuditContext context) {
        if (legacy != null) return legacy.assignRoles(userId, tenantId, roleIds, actor, context.toLegacy());
        return directRoles.assign(userId, tenantId, roleIds, actor, context.toLegacy());
    }

    public long bulkAssignRole(BulkAssignRoleRequest request, String tenantId, ErgPrincipal actor,
            AuthorizationAuditContext context) {
        return legacy != null ? legacy.bulkAssignRole(request, tenantId, actor, context.toLegacy())
                : directRoles.bulk(request, tenantId, actor, context.toLegacy());
    }

    public ScopedRoleAssignmentResponse assignScopedRole(String userId, String tenantId,
            AssignScopedRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        requireScoped();
        return scoped.assign(userId, tenantId, request, actor, context.toLegacy());
    }

    public ScopedRoleAssignmentResponse revokeScopedRole(String userId, String assignmentId, String tenantId,
            RevokeScopedRoleRequest request, ErgPrincipal actor, AuthorizationAuditContext context) {
        requireScoped();
        return scoped.revoke(userId, assignmentId, tenantId, request, actor, context.toLegacy());
    }

    public PermissionOverrideResponse addPermissionOverride(String userId, AddPermissionOverrideRequest request,
            String actorId, String tenantId, AuthorizationAuditContext context) {
        return legacy != null ? legacy.addPermissionOverride(userId, request, actorId, tenantId, context.toLegacy())
                : overrides.add(userId, request, actorId, tenantId, context.toLegacy());
    }

    public void setPermissionGroupRepository(
            com.erg.ergspringboot.repository.authorization.PermissionGroupRepository value) {
        if (catalog != null) catalog.setPermissionGroupRepository(value);
    }

    public void setPermissionBindingRepository(
            com.erg.ergspringboot.repository.authorization.PermissionBindingRepository value) {
        if (catalog != null) catalog.setPermissionBindingRepository(value);
    }

    public void setUserRoleAssignmentRepository(
            com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository value) {
        if (roles != null) roles.setUserRoleAssignmentRepository(value);
    }

    public void setMutationAuditService(AuthorizationMutationAuditService value) {
        if (roles != null) roles.setMutationAuditService(value);
        if (directRoles != null) directRoles.setMutationAuditService(value);
        if (overrides != null) overrides.setMutationAuditService(value);
    }

    public void deletePermissionOverride(String userId, String overrideId, String tenantId,
            AuthorizationAuditContext context) {
        if (legacy != null) legacy.deletePermissionOverride(userId, overrideId, tenantId, context.toLegacy());
        else overrides.delete(userId, overrideId, tenantId, context.toLegacy());
    }

    public EffectivePermissionsResponse effectivePermissions(String userId) {
        return effectivePermissions(userId, currentTenant());
    }

    public EffectivePermissionsResponse effectivePermissions(String userId, String tenantId) {
        return legacy != null ? legacy.effectivePermissions(userId, tenantId)
                : access.effective(userId, normalizeTenant(tenantId));
    }

    public List<PermissionOverrideResponse> listPermissionOverrides(String userId) {
        return listPermissionOverrides(userId, currentTenant());
    }

    public List<PermissionOverrideResponse> listPermissionOverrides(String userId, String tenantId) {
        return legacy != null ? legacy.listPermissionOverrides(userId, tenantId)
                : access.listOverrides(userId, normalizeTenant(tenantId));
    }

    public FeatureConfigResponse featureConfig(String userId) {
        return featureConfig(userId, currentTenant());
    }

    public FeatureConfigResponse featureConfig(String userId, String tenantId) {
        return legacy != null ? legacy.featureConfig(userId, tenantId)
                : access.feature(userId, normalizeTenant(tenantId));
    }

    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request) {
        return previewChanges(userId, request, currentTenant());
    }

    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request, String tenantId) {
        return legacy != null ? legacy.previewChanges(userId, request, tenantId)
                : access.preview(userId, request, normalizeTenant(tenantId));
    }

    private void requireScoped() {
        if (scoped == null) throw new IllegalStateException("scoped role assignment service is not configured");
    }

    private String currentTenant() {
        ErgPrincipal actor = SecurityUtils.principal();
        return actor == null || actor.tenantId() == null || actor.tenantId().isBlank()
                ? "erg" : actor.tenantId().trim();
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }
}
