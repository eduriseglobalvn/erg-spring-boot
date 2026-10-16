package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.authorization.AuthorizationCommandOperations;
import com.erg.ergspringboot.authorization.AuthorizationAuditContext;
import com.erg.ergspringboot.authorization.AuthorizationOperationsCompatibility;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AddPermissionOverrideRequest;
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
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link AuthorizationCommandOperations}. This bean is a compatibility adapter only;
 * authorization use cases are owned by the explicit module.
 */
@Deprecated
@Service
public class AuthorizationService {
    private final AuthorizationCommandOperations operations;

    @Autowired
    public AuthorizationService(AuthorizationCommandOperations operations) {
        this.operations = operations;
    }

    /** Compatibility constructor retained for focused unit tests and old embedders. */
    public AuthorizationService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository overrideRepository,
            UserRepository userRepository) {
        this(AuthorizationOperationsCompatibility.fromRepositories(
                roleRepository, permissionRepository, overrideRepository, userRepository));
    }

    public List<RoleResponse> listRoles() { return operations.listRoles(); }
    public RoleResponse getRole(String id) { return operations.getRole(id); }
    public RoleResponse createRole(CreateRoleRequest request) {
        ErgPrincipal actor = SecurityUtils.requirePrincipal();
        return createRole(request, actor, context(actor));
    }
    public RoleResponse createRole(CreateRoleRequest request, ErgPrincipal actor, AuditContext context) {
        return operations.createRole(request, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public RoleResponse updateRole(String id, UpdateRoleRequest request) {
        return updateRole(id, request, SecurityUtils.principal());
    }
    public RoleResponse updateRole(String id, UpdateRoleRequest request, ErgPrincipal actor) {
        return updateRole(id, request, actor, context(actor));
    }
    public RoleResponse updateRole(String id, UpdateRoleRequest request, ErgPrincipal actor, AuditContext context) {
        return operations.updateRole(id, request, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public void deleteRole(String id) { deleteRole(id, SecurityUtils.principal()); }
    public void deleteRole(String id, ErgPrincipal actor) { deleteRole(id, actor, context(actor)); }
    public void deleteRole(String id, ErgPrincipal actor, AuditContext context) {
        operations.deleteRole(id, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public List<PermissionResponse> listPermissions() { return operations.listPermissions(); }
    public PermissionPageResponse searchPermissions(Integer page, Integer limit, String search, String group,
            String portal, String status, Boolean used) {
        return operations.searchPermissions(page, limit, search, group, portal, status, used);
    }
    public PermissionResponse getPermission(String id) { return operations.getPermission(id); }
    public List<PermissionGroupResponse> listPermissionGroups() { return operations.listPermissionGroups(); }
    public RoleResponse assignRoles(String userId, List<String> roleIds) {
        return assignRoles(userId, currentTenant(), roleIds, SecurityUtils.principal());
    }
    public RoleResponse assignRoles(String userId, String tenant, List<String> roleIds, ErgPrincipal actor) {
        return assignRoles(userId, tenant, roleIds, actor, context(actor));
    }
    public RoleResponse assignRoles(String userId, String tenant, List<String> roleIds, ErgPrincipal actor,
            AuditContext context) {
        return operations.assignRoles(userId, tenant, roleIds, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public void removeRole(String userId, String roleId) {
        removeRole(userId, roleId, currentTenant(), SecurityUtils.principal());
    }
    public void removeRole(String userId, String roleId, String tenant, ErgPrincipal actor) {
        removeRole(userId, roleId, tenant, actor, context(actor));
    }
    public void removeRole(String userId, String roleId, String tenant, ErgPrincipal actor, AuditContext context) {
        operations.removeRole(userId, roleId, tenant, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public long bulkAssignRole(BulkAssignRoleRequest request) {
        return bulkAssignRole(request, currentTenant(), SecurityUtils.principal());
    }
    public long bulkAssignRole(BulkAssignRoleRequest request, String tenant, ErgPrincipal actor) {
        return bulkAssignRole(request, tenant, actor, context(actor));
    }
    public long bulkAssignRole(BulkAssignRoleRequest request, String tenant, ErgPrincipal actor, AuditContext context) {
        return operations.bulkAssignRole(request, tenant, actor, new AuthorizationAuditContext(
                context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public PermissionOverrideResponse addPermissionOverride(String userId, AddPermissionOverrideRequest request,
            String actorId) {
        return addPermissionOverride(userId, request, actorId, currentTenant(), context(SecurityUtils.principal()));
    }
    public PermissionOverrideResponse addPermissionOverride(String userId, AddPermissionOverrideRequest request,
            String actorId, String tenant, AuditContext context) {
        return operations.addPermissionOverride(userId, request, actorId, tenant,
                new AuthorizationAuditContext(
                        context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public void deletePermissionOverride(String userId, String overrideId) {
        deletePermissionOverride(userId, overrideId, currentTenant());
    }
    public void deletePermissionOverride(String userId, String overrideId, String tenant) {
        deletePermissionOverride(userId, overrideId, tenant, context(SecurityUtils.principal()));
    }
    public void deletePermissionOverride(String userId, String overrideId, String tenant, AuditContext context) {
        operations.deletePermissionOverride(userId, overrideId, tenant,
                new AuthorizationAuditContext(
                        context.actorUserId(), context.requestId(), context.ipAddress(), context.userAgent()));
    }
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId) {
        return operations.listPermissionOverrides(userId);
    }
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId, String tenant) {
        return operations.listPermissionOverrides(userId, tenant);
    }
    public EffectivePermissionsResponse effectivePermissions(String userId) { return operations.effectivePermissions(userId); }
    public EffectivePermissionsResponse effectivePermissions(String userId, String tenant) { return operations.effectivePermissions(userId, tenant); }
    public FeatureConfigResponse featureConfig(String userId) { return operations.featureConfig(userId); }
    public FeatureConfigResponse featureConfig(String userId, String tenant) { return operations.featureConfig(userId, tenant); }
    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request) {
        return operations.previewChanges(userId, request);
    }
    public PreviewChangesResponse previewChanges(String userId, PreviewChangesRequest request, String tenant) {
        return operations.previewChanges(userId, request, tenant);
    }

    public void setPermissionGroupRepository(com.erg.ergspringboot.repository.authorization.PermissionGroupRepository value) {
        operations.setPermissionGroupRepository(value);
    }
    public void setPermissionBindingRepository(com.erg.ergspringboot.repository.authorization.PermissionBindingRepository value) {
        operations.setPermissionBindingRepository(value);
    }
    public void setUserRoleAssignmentRepository(com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository value) {
        operations.setUserRoleAssignmentRepository(value);
    }
    public void setMutationAuditService(AuthorizationMutationAuditService value) {
        operations.setMutationAuditService(value);
    }

    private AuditContext context(ErgPrincipal actor) {
        return new AuditContext(actor == null ? null : actor.userId(), null, null, null);
    }
    private String currentTenant() {
        ErgPrincipal actor = SecurityUtils.principal();
        return actor == null || actor.tenantId() == null || actor.tenantId().isBlank() ? "erg" : actor.tenantId().trim();
    }
    private String normalize(String value) { return value == null || value.isBlank() ? "erg" : value.trim(); }
}
