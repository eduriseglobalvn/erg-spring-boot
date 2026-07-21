package com.erg.ergspringboot.authorization.internal.roledefinition;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.model.authorization.*;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.*;
import com.erg.ergspringboot.repository.authorization.*;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Role definition CRUD, protected-role invariants and authorization-version invalidation. */
@Service
public class AuthorizationRoleDefinitionUseCase {
    private final RoleRepository roles; private final PermissionRepository permissions;
    private UserRoleAssignmentRepository assignments; private AuthorizationMutationAuditService audit;
    private final ProtectedRolePolicy policy=new ProtectedRolePolicy();
    public AuthorizationRoleDefinitionUseCase(RoleRepository roles,PermissionRepository permissions){this.roles=roles;this.permissions=permissions;}
    public void setUserRoleAssignmentRepository(UserRoleAssignmentRepository value){this.assignments=value;} public void setMutationAuditService(AuthorizationMutationAuditService value){this.audit=value;}
    @Transactional(readOnly=true) @Cacheable(cacheNames="authorizationRoles",key="'all'") public List<RoleResponse> list(){List<Role> all=roles.findAllWithPermissionsAndUsers();Map<String,List<UserRoleAssignment>> by=active(all);return all.stream().filter(r->!"system.god_mode".equalsIgnoreCase(r.getName())).map(r->response(r,by.getOrDefault(r.getId(),List.of()))).toList();}
    @Transactional(readOnly=true) public RoleResponse get(String id){Role role=find(id);if("system.god_mode".equalsIgnoreCase(role.getName()))throw new ResourceNotFoundException("role not found");return response(role);}
    @Transactional @CacheEvict(cacheNames={"authorizationRoles","authorizationPermissions"},allEntries=true) public RoleResponse create(CreateRoleRequest request,ErgPrincipal actor,AuditContext context){policy.assertRoleDefinitionAllowed(request.name());AuditContext c=required(actor,context);Role role=new Role();role.setId(IdGenerator.newId());role.setName(request.name().trim());role.setDescription(request.description());role.setIsDefault(false);role.setUpdatedBy(c.actorUserId());role.setPermissions(resolve(request.permissions()));role=roles.save(role);audit().recordRoleMutation(tenant(actor),role.getId(),"ROLE_CREATED",c,Map.of("roleName",role.getName()));return response(role);}
    @Transactional @CacheEvict(cacheNames={"authorizationRoles","authorizationPermissions"},allEntries=true) public RoleResponse update(String id,UpdateRoleRequest request,ErgPrincipal actor,AuditContext context){Role role=find(id);policy.assertCanManageDefinition(actor,role);if(request.name()!=null){policy.assertRoleDefinitionAllowed(request.name());role.setName(request.name().trim());}if(request.description()!=null)role.setDescription(request.description());if(request.permissions()!=null)role.setPermissions(resolve(request.permissions()));List<UserRef> users=affected(role);AuditContext c=required(actor,context);role.setUpdatedBy(c.actorUserId());role=roles.save(role);audit().recordRoleMutation(tenant(actor),role.getId(),"ROLE_UPDATED",c,Map.of("roleName",role.getName()));audit().authorizationChanged(users,role.getId(),"ROLE_UPDATED",c);return response(role);}
    @Transactional @CacheEvict(cacheNames={"authorizationRoles","authorizationPermissions"},allEntries=true) public void delete(String id,ErgPrincipal actor,AuditContext context){Role role=find(id);policy.assertCanManageDefinition(actor,role);List<UserRef> users=affected(role);String name=role.getName();roles.delete(role);AuditContext c=required(actor,context);audit().recordRoleMutation(tenant(actor),role.getId(),"ROLE_DELETED",c,Map.of("roleName",name));audit().authorizationChanged(users,role.getId(),"ROLE_DELETED",c);}
    private Role find(String id){return roles.findById(id).or(()->roles.findByNameIgnoreCase(id)).orElseThrow(()->new ResourceNotFoundException("role not found"));}
    private LinkedHashSet<Permission> resolve(List<String> names){if(names==null||names.isEmpty())return new LinkedHashSet<>();List<Permission> found=permissions.findByNameInIgnoreCase(names);if(found.size()!=new LinkedHashSet<>(names).size())throw new BadRequestException("one or more permissions were not found");return new LinkedHashSet<>(found);}
    private List<UserRef> affected(Role role){LinkedHashSet<UserRef> result=new LinkedHashSet<>();if(role.getUsers()!=null)role.getUsers().stream().filter(Objects::nonNull).filter(u->u.getId()!=null&&!u.getId().isBlank()).map(u->new UserRef(u.getId(),tenant(u.getTenantId()))).forEach(result::add);if(assignments!=null)assignments.findActiveForRole(role.getId(),OffsetDateTime.now()).stream().filter(Objects::nonNull).map(a->new UserRef(a.getUserId(),tenant(a.getTenantId()))).forEach(result::add);return List.copyOf(result);}
    private Map<String,List<UserRoleAssignment>> active(List<Role> values){if(assignments==null||values.isEmpty())return Map.of();return assignments.findActiveForRoleIds(values.stream().map(Role::getId).filter(Objects::nonNull).toList(),OffsetDateTime.now(ZoneOffset.UTC)).stream().collect(Collectors.groupingBy(UserRoleAssignment::getRoleId,LinkedHashMap::new,Collectors.toList()));}
    private RoleResponse response(Role role){return response(role,active(List.of(role)).getOrDefault(role.getId(),List.of()));}
    private RoleResponse response(Role role,List<UserRoleAssignment> scoped){LinkedHashSet<String> users=role.getUsers()==null?new LinkedHashSet<>():role.getUsers().stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));scoped.stream().map(UserRoleAssignment::getUserId).filter(Objects::nonNull).forEach(users::add);List<String> types=scoped.stream().map(UserRoleAssignment::getScopeType).filter(Objects::nonNull).distinct().sorted().toList();boolean system=Boolean.TRUE.equals(role.getIsDefault())||(role.getName()!=null&&role.getName().startsWith("system."));return new RoleResponse(role.getId(),role.getName(),role.getDescription(),role.getPermissions()==null?List.of():role.getPermissions().stream().map(Permission::getName).toList(),Boolean.TRUE.equals(role.getIsDefault()),role.getCreatedAt(),role.getUpdatedAt(),users.size(),types,system,"ACTIVE",role.getUpdatedBy());}
    private AuthorizationMutationAuditService audit(){if(audit==null)throw new IllegalStateException("authorization mutation audit service is not configured");return audit;}
    private AuditContext required(ErgPrincipal actor,AuditContext context){AuditContext c=context==null?new AuditContext(actor==null?null:actor.userId(),null,null,null):context;if(c.actorUserId()==null||c.actorUserId().isBlank())throw new BadRequestException("authorization mutation actor is required");return c;}
    private String tenant(ErgPrincipal actor){return tenant(actor==null?null:actor.tenantId());}private String tenant(String v){return v==null||v.isBlank()?"erg":v.trim();}
}
