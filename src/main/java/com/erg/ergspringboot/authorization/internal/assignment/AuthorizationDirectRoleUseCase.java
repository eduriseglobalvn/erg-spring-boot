package com.erg.ergspringboot.authorization.internal.assignment;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.BulkAssignRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RoleResponse;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.model.authorization.*;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.*;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService.UserRef;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Direct user-role assignment commands with tenant and authz-version invariants. */
@Service
public class AuthorizationDirectRoleUseCase {
    private final UserRepository users; private final RoleRepository roles; private AuthorizationMutationAuditService audit; private final ProtectedRolePolicy policy=new ProtectedRolePolicy();
    public AuthorizationDirectRoleUseCase(UserRepository users,RoleRepository roles,AuthorizationMutationAuditService audit){this.users=users;this.roles=roles;this.audit=audit;}
    public void setMutationAuditService(AuthorizationMutationAuditService audit){this.audit=audit;}
    @Transactional public RoleResponse assign(String userId,String tenant,List<String> roleIds,ErgPrincipal actor,AuditContext context){User user=find(userId,tenant);List<Role> found=resolve(roleIds);if(found.size()!=new LinkedHashSet<>(roleIds).size())throw new BadRequestException("one or more roles were not found");policy.assertCanGrant(actor,found);user.setRoles(new LinkedHashSet<>(found));users.save(user);audit.authorizationChanged(List.of(new UserRef(user.getId(),normalize(tenant))),found.stream().map(Role::getId).sorted().collect(Collectors.joining(",")),"DIRECT_ROLE_ASSIGNMENT_REPLACED",required(actor,context));return response(found.stream().findFirst().orElseThrow(()->new BadRequestException("at least one role is required")));}
    @Transactional public void remove(String userId,String roleId,String tenant,ErgPrincipal actor,AuditContext context){User user=find(userId,tenant);Role role=roles.findById(roleId).or(()->roles.findByNameIgnoreCase(roleId)).orElseThrow(()->new ResourceNotFoundException("role not found"));policy.assertCanGrant(actor,List.of(role));Set<Role> current=user.getRoles()==null?new LinkedHashSet<>():new LinkedHashSet<>(user.getRoles());current.removeIf(r->r.getId().equals(role.getId())||r.getName().equalsIgnoreCase(role.getName()));user.setRoles(current);users.save(user);audit.authorizationChanged(List.of(new UserRef(user.getId(),normalize(tenant))),role.getId(),"DIRECT_ROLE_REMOVED",required(actor,context));}
    @Transactional public long bulk(BulkAssignRoleRequest request,String tenant,ErgPrincipal actor,AuditContext context){Role role=roles.findById(request.roleId()).orElseThrow(()->new ResourceNotFoundException("role not found"));policy.assertCanGrant(actor,List.of(role));List<User> found=users.findByTenantIdAndIdInAnyStatus(normalize(tenant),request.userIds());if(found.size()!=new LinkedHashSet<>(request.userIds()).size())throw new ResourceNotFoundException("one or more users were not found in the authenticated tenant");found.forEach(user->{Set<Role> current=user.getRoles()==null?new LinkedHashSet<>():new LinkedHashSet<>(user.getRoles());current.add(role);user.setRoles(current);});users.saveAll(found);audit.authorizationChanged(found.stream().map(u->new UserRef(u.getId(),normalize(tenant))).toList(),role.getId(),"DIRECT_ROLE_ASSIGNED",required(actor,context));return found.size();}
    private User find(String id,String tenant){return users.findByIdAndTenantId(id,normalize(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));}
    private List<Role> resolve(List<String> ids){if(ids==null||ids.isEmpty())return List.of();List<Role> byId=roles.findAllById(ids);Set<String> found=byId.stream().flatMap(r->java.util.stream.Stream.of(r.getId().toLowerCase(),r.getName().toLowerCase())).collect(Collectors.toSet());List<String> missing=ids.stream().filter(id->!found.contains(id.toLowerCase())).toList();List<Role> byName=missing.isEmpty()?List.of():roles.findByNameInIgnoreCase(missing);return java.util.stream.Stream.concat(byId.stream(),byName.stream()).collect(Collectors.collectingAndThen(Collectors.toMap(Role::getId,r->r,(a,b)->a,LinkedHashMap::new),m->new ArrayList<>(m.values())));}
    private RoleResponse response(Role r){return new RoleResponse(r.getId(),r.getName(),r.getDescription(),r.getPermissions()==null?List.of():r.getPermissions().stream().map(Permission::getName).toList(),Boolean.TRUE.equals(r.getIsDefault()),r.getCreatedAt(),r.getUpdatedAt(),r.getUsers()==null?0:r.getUsers().size(),List.of(),Boolean.TRUE.equals(r.getIsDefault()),"ACTIVE",r.getUpdatedBy());}
    private AuditContext required(ErgPrincipal a,AuditContext c){AuditContext x=c==null?new AuditContext(a==null?null:a.userId(),null,null,null):c;if(x.actorUserId()==null||x.actorUserId().isBlank())throw new BadRequestException("authorization mutation actor is required");return x;}
    private String normalize(String v){return v==null||v.isBlank()?"erg":v.trim();}
}
