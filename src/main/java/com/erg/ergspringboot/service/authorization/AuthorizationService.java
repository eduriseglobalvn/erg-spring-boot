package com.erg.ergspringboot.service.authorization;

import com.erg.ergspringboot.authorization.internal.access.AuthorizationAccessQueryUseCase;
import com.erg.ergspringboot.authorization.internal.assignment.AuthorizationDirectRoleUseCase;
import com.erg.ergspringboot.authorization.internal.catalog.AuthorizationPermissionCatalogUseCase;
import com.erg.ergspringboot.authorization.internal.override.AuthorizationPermissionOverrideUseCase;
import com.erg.ergspringboot.authorization.internal.roledefinition.AuthorizationRoleDefinitionUseCase;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.*;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Compatibility facade; authorization behavior is owned by internal use cases. */
@Service
public class AuthorizationService {
    private final AuthorizationPermissionCatalogUseCase catalog;
    private final AuthorizationRoleDefinitionUseCase roles;
    private final AuthorizationAccessQueryUseCase access;
    private final AuthorizationDirectRoleUseCase directRoles;
    private final AuthorizationPermissionOverrideUseCase overrides;

    @Autowired
    public AuthorizationService(AuthorizationPermissionCatalogUseCase catalog,
            AuthorizationRoleDefinitionUseCase roles, AuthorizationAccessQueryUseCase access,
            AuthorizationDirectRoleUseCase directRoles, AuthorizationPermissionOverrideUseCase overrides) {
        this.catalog=catalog; this.roles=roles; this.access=access; this.directRoles=directRoles; this.overrides=overrides;
    }

    /** Compatibility constructor retained for legacy tests/callers. */
    public AuthorizationService(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserPermissionOverrideRepository overrideRepository, UserRepository userRepository) {
        this(new AuthorizationPermissionCatalogUseCase(permissionRepository,roleRepository,overrideRepository),
                new AuthorizationRoleDefinitionUseCase(roleRepository,permissionRepository),
                new AuthorizationAccessQueryUseCase(userRepository,overrideRepository),
                new AuthorizationDirectRoleUseCase(userRepository,roleRepository,null),
                new AuthorizationPermissionOverrideUseCase(userRepository,permissionRepository,overrideRepository,null));
    }

    @Autowired(required=false) void setPermissionGroupRepository(PermissionGroupRepository value){catalog.setPermissionGroupRepository(value);}
    @Autowired(required=false) void setPermissionBindingRepository(PermissionBindingRepository value){catalog.setPermissionBindingRepository(value);}
    @Autowired(required=false) void setUserRoleAssignmentRepository(UserRoleAssignmentRepository value){roles.setUserRoleAssignmentRepository(value);}
    @Autowired(required=false) void setMutationAuditService(AuthorizationMutationAuditService value){roles.setMutationAuditService(value);directRoles.setMutationAuditService(value);overrides.setMutationAuditService(value);}

    public List<RoleResponse> listRoles(){return roles.list();}
    public RoleResponse getRole(String id){return roles.get(id);}
    public RoleResponse createRole(CreateRoleRequest request){ErgPrincipal actor=SecurityUtils.requirePrincipal();return createRole(request,actor,context(actor));}
    public RoleResponse createRole(CreateRoleRequest request,ErgPrincipal actor,AuditContext context){return roles.create(request,actor,context);}
    public RoleResponse updateRole(String id,UpdateRoleRequest request){return updateRole(id,request,SecurityUtils.principal());}
    public RoleResponse updateRole(String id,UpdateRoleRequest request,ErgPrincipal actor){return updateRole(id,request,actor,context(actor));}
    public RoleResponse updateRole(String id,UpdateRoleRequest request,ErgPrincipal actor,AuditContext context){return roles.update(id,request,actor,context);}
    public void deleteRole(String id){deleteRole(id,SecurityUtils.principal());}
    public void deleteRole(String id,ErgPrincipal actor){deleteRole(id,actor,context(actor));}
    public void deleteRole(String id,ErgPrincipal actor,AuditContext context){roles.delete(id,actor,context);}
    public List<PermissionResponse> listPermissions(){return catalog.list();}
    public PermissionPageResponse searchPermissions(Integer page,Integer limit,String search,String group,String portal,String status,Boolean used){return catalog.search(page,limit,search,group,portal,status,used);}
    public PermissionResponse getPermission(String id){return catalog.get(id);}
    public List<PermissionGroupResponse> listPermissionGroups(){return catalog.groups();}
    public RoleResponse assignRoles(String userId,List<String> roleIds){return assignRoles(userId,currentTenant(),roleIds,SecurityUtils.principal());}
    public RoleResponse assignRoles(String userId,String tenant,List<String> roleIds,ErgPrincipal actor){return assignRoles(userId,tenant,roleIds,actor,context(actor));}
    public RoleResponse assignRoles(String userId,String tenant,List<String> roleIds,ErgPrincipal actor,AuditContext context){return directRoles.assign(userId,tenant,roleIds,actor,context);}
    public void removeRole(String userId,String roleId){removeRole(userId,roleId,currentTenant(),SecurityUtils.principal());}
    public void removeRole(String userId,String roleId,String tenant,ErgPrincipal actor){removeRole(userId,roleId,tenant,actor,context(actor));}
    public void removeRole(String userId,String roleId,String tenant,ErgPrincipal actor,AuditContext context){directRoles.remove(userId,roleId,tenant,actor,context);}
    public long bulkAssignRole(BulkAssignRoleRequest request){return bulkAssignRole(request,currentTenant(),SecurityUtils.principal());}
    public long bulkAssignRole(BulkAssignRoleRequest request,String tenant,ErgPrincipal actor){return bulkAssignRole(request,tenant,actor,context(actor));}
    public long bulkAssignRole(BulkAssignRoleRequest request,String tenant,ErgPrincipal actor,AuditContext context){return directRoles.bulk(request,tenant,actor,context);}
    public PermissionOverrideResponse addPermissionOverride(String userId,AddPermissionOverrideRequest request,String actorId){return addPermissionOverride(userId,request,actorId,currentTenant());}
    public PermissionOverrideResponse addPermissionOverride(String userId,AddPermissionOverrideRequest request,String actorId,String tenant){return addPermissionOverride(userId,request,actorId,tenant,new AuditContext(actorId,null,null,null));}
    public PermissionOverrideResponse addPermissionOverride(String userId,AddPermissionOverrideRequest request,String actorId,String tenant,AuditContext context){return overrides.add(userId,request,actorId,tenant,context);}
    public void deletePermissionOverride(String userId,String overrideId){deletePermissionOverride(userId,overrideId,currentTenant());}
    public void deletePermissionOverride(String userId,String overrideId,String tenant){deletePermissionOverride(userId,overrideId,tenant,context(SecurityUtils.principal()));}
    public void deletePermissionOverride(String userId,String overrideId,String tenant,AuditContext context){overrides.delete(userId,overrideId,tenant,context);}
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId){return listPermissionOverrides(userId,currentTenant());}
    public List<PermissionOverrideResponse> listPermissionOverrides(String userId,String tenant){return access.listOverrides(userId,tenant);}
    public EffectivePermissionsResponse effectivePermissions(String userId){return effectivePermissions(userId,currentTenant());}
    public EffectivePermissionsResponse effectivePermissions(String userId,String tenant){return access.effective(userId,tenant);}
    public FeatureConfigResponse featureConfig(String userId){return featureConfig(userId,currentTenant());}
    public FeatureConfigResponse featureConfig(String userId,String tenant){return access.feature(userId,tenant);}
    public PreviewChangesResponse previewChanges(String userId,PreviewChangesRequest request){return previewChanges(userId,request,currentTenant());}
    public PreviewChangesResponse previewChanges(String userId,PreviewChangesRequest request,String tenant){return access.preview(userId,request,tenant);}

    private AuditContext context(ErgPrincipal actor){return new AuditContext(actor==null?null:actor.userId(),null,null,null);}
    private String currentTenant(){ErgPrincipal actor=SecurityUtils.principal();return actor==null?"erg":normalize(actor.tenantId());}
    private String normalize(String tenant){return tenant==null||tenant.isBlank()?"erg":tenant.trim();}
}
