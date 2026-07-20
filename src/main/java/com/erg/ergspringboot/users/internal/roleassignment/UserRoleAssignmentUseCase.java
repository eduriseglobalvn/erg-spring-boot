package com.erg.ergspringboot.users.internal.roleassignment;

import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ProtectedRolePolicy;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns direct role and permission overrides for a user. */
@Service
public class UserRoleAssignmentUseCase {
    private static final String DEFAULT_TENANT="erg";
    private final UserRepository users; private final RoleRepository roles; private final PermissionRepository permissions;
    private final UserPermissionOverrideRepository overrides; private final UserActivityRepository activities;
    private AuthorizationMutationAuditService authz; private final ProtectedRolePolicy rolePolicy=new ProtectedRolePolicy();
    public UserRoleAssignmentUseCase(UserRepository users,RoleRepository roles,PermissionRepository permissions,
            UserPermissionOverrideRepository overrides,UserActivityRepository activities,AuthorizationMutationAuditService authz){
        this.users=users;this.roles=roles;this.permissions=permissions;this.overrides=overrides;this.activities=activities;this.authz=authz;}
    public void setAuthorizationMutationAuditService(AuthorizationMutationAuditService authz){this.authz=authz;}
    @Transactional public UserResponse assignRoles(String userId,String tenantId,AdminAssignRolesRequest request,
            UserAuditContext audit,ErgPrincipal actor){
        User user=find(userId,tenantId);List<String> before=roleNames(user.getRoles());
        List<String> names=request.roles().stream().map(this::trim).filter(v->v!=null).toList();List<Role> found=roles.findByNameInIgnoreCase(names);
        if(found.size()!=new LinkedHashSet<>(names).size())throw new BadRequestException("one or more roles were not found");rolePolicy.assertCanGrant(actor,found);
        user.setRoles(new LinkedHashSet<>(found));users.save(user);record(user,audit,"USER_ROLES_CHANGED",Map.of("roles",before),Map.of("roles",roleNames(user.getRoles())));
        authz.authorizationChanged(List.of(new UserRef(user.getId(),tenant(tenantId))),found.stream().map(Role::getId).sorted().collect(Collectors.joining(",")),"USER_ROLES_CHANGED",legacy(audit == null ? new UserAuditContext(user.getId(),null,null,null) : audit));
        return response(user);}
    @Transactional public PermissionOverrideResponse assignPermissions(String userId,String tenantId,AdminAssignPermissionsRequest request,UserAuditContext audit){
        User user=find(userId,tenantId);String grant=grant(request.grantType());List<String> names=request.permissions().stream().map(this::trim).filter(v->v!=null).toList();List<Permission> found=permissions.findByNameInIgnoreCase(names);
        if(found.size()!=new LinkedHashSet<>(names).size())throw new BadRequestException("one or more permissions were not found");overrides.deleteByUserId(userId);
        List<UserPermissionOverride> saved=found.stream().map(p->{UserPermissionOverride o=new UserPermissionOverride();o.setId(IdGenerator.newId());o.setUserId(userId);o.setPermission(p.getName());o.setGrantType(grant);o.setReason(trim(request.reason()));o.setExpiresAt(parse(request.expiresAt()));o.setCreatedAt(OffsetDateTime.now());return o;}).toList();overrides.saveAll(saved);
        record(user,audit,"USER_PERMISSIONS_CHANGED",Map.of(),Map.of("grantType",grant,"permissions",names));
        authz.authorizationChanged(List.of(new UserRef(user.getId(),tenant(tenantId))),found.stream().map(Permission::getId).sorted().collect(Collectors.joining(",")),"USER_PERMISSIONS_CHANGED",legacy(audit == null ? new UserAuditContext(user.getId(),null,null,null) : audit));
        return new PermissionOverrideResponse(userId,found.stream().map(Permission::getName).toList(),grant);}
    private User find(String id,String tenant){return users.findByIdAndTenantId(id,tenant(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));}
    private void record(User target,UserAuditContext audit,String action,Map<String,Object> before,Map<String,Object> after){UserAuditContext c=audit==null?new UserAuditContext(target.getId(),null,null,null):audit;UserActivity a=new UserActivity();a.setId(IdGenerator.newId());a.setUserId(blank(c.actorUserId())?target.getId():c.actorUserId());a.setTenantId(tenant(target.getTenantId()));a.setAction(action);a.setTargetUserId(target.getId());a.setIpAddress(c.ipAddress());a.setUserAgent(c.userAgent());a.setRequestId(c.requestId());a.setDescription("User authorization changed");a.setMetadata(Map.of("before",before,"after",after));a.setCreatedAt(OffsetDateTime.now());activities.save(a);}
    private UserResponse response(User u){return new UserResponse(u.getId(),u.getEmail(),u.getFullName(),u.getPhone(),u.getAvatarUrl(),u.getBio(),u.getGender(),u.getDateOfBirth(),u.getAddress(),u.getCity(),u.getDistrict(),u.getJobTitle(),u.getRegion(),u.getStatus(),u.getProvider(),u.getAccountType(),u.getLastLoginProvider(),roleNames(u.getRoles()),u.getSocialLinks()==null?Map.of():u.getSocialLinks(),Boolean.TRUE.equals(u.getIsProfileCompleted()),iso(u.getLastLoginAt()),u.getLoginCount(),u.getTenantId(),iso(u.getCreatedAt()),iso(u.getUpdatedAt()),lifecycle(u));}
    private AccountLifecycleResponse lifecycle(User u){List<String> n=new ArrayList<>();if(!Boolean.TRUE.equals(u.getIsProfileCompleted()))n.add("COMPLETE_PROFILE");if(!Boolean.TRUE.equals(u.getRecoveryEmailVerified()))n.add("VERIFY_RECOVERY_EMAIL");if(Boolean.TRUE.equals(u.getMustChangePassword()))n.add("CHANGE_PASSWORD");String s=n.isEmpty()?"ACTIVE":("INVITED".equalsIgnoreCase(u.getStatus())?"PROVISIONED":"FIRST_LOGIN_RESTRICTED");String e=u.getRecoveryEmail();String m=e==null||!e.contains("@")?null:e.substring(0,1)+"***"+e.substring(e.indexOf('@'));return new AccountLifecycleResponse(s,Boolean.TRUE.equals(u.getIsProfileCompleted()),m,Boolean.TRUE.equals(u.getRecoveryEmailVerified()),Boolean.TRUE.equals(u.getMustChangePassword()),List.copyOf(n));}
    private List<String> roleNames(Set<Role> r){if(r==null||r.isEmpty())return List.of("user");return r.stream().map(Role::getName).filter(v->v!=null&&!v.isBlank()).toList();}
    private String grant(String v){String g=v.trim().toUpperCase();if(!List.of("GRANT","DENY").contains(g))throw new BadRequestException("invalid permission grant type");return g;}
    private OffsetDateTime parse(String v){return blank(v)?null:OffsetDateTime.parse(v);}private String trim(String v){return blank(v)?null:v.trim();}private String tenant(String v){return blank(v)?DEFAULT_TENANT:v.trim();}private boolean blank(String v){return v==null||v.isBlank();}private String iso(OffsetDateTime v){return v==null?null:v.toString();}
    private UserService.AuditContext legacy(UserAuditContext c){return c==null?new UserService.AuditContext(null,null,null,null):new UserService.AuditContext(c.actorUserId(),c.requestId(),c.ipAddress(),c.userAgent());}
}
