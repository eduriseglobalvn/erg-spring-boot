package com.erg.ergspringboot.authorization.internal.roledefinition;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.users.persistence.authorization.*;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only role catalog queries; role definitions are a fixed code catalog. */
@Service
public class AuthorizationRoleDefinitionUseCase {
    private final RoleRepository roles;
    private UserRoleAssignmentRepository assignments;
    public AuthorizationRoleDefinitionUseCase(RoleRepository roles){this.roles=roles;}
    @Autowired(required=false) public void setUserRoleAssignmentRepository(UserRoleAssignmentRepository value){this.assignments=value;}
    @Transactional(readOnly=true) @Cacheable(cacheNames="authorizationRoles",key="'all'") public List<RoleResponse> list(){List<Role> all=roles.findAllWithPermissionsAndUsers();Map<String,List<UserRoleAssignment>> by=active(all);return all.stream().filter(r->!"system.god_mode".equalsIgnoreCase(r.getName())).map(r->response(r,by.getOrDefault(r.getId(),List.of()))).toList();}
    @Transactional(readOnly=true) public RoleResponse get(String id){Role role=find(id);if("system.god_mode".equalsIgnoreCase(role.getName()))throw new ResourceNotFoundException("role not found");return response(role);}
    private Role find(String id){return roles.findById(id).or(()->roles.findByNameIgnoreCase(id)).orElseThrow(()->new ResourceNotFoundException("role not found"));}
    private Map<String,List<UserRoleAssignment>> active(List<Role> values){if(assignments==null||values.isEmpty())return Map.of();return assignments.findActiveForRoleIds(values.stream().map(Role::getId).filter(Objects::nonNull).toList(),OffsetDateTime.now(ZoneOffset.UTC)).stream().collect(Collectors.groupingBy(UserRoleAssignment::getRoleId,LinkedHashMap::new,Collectors.toList()));}
    private RoleResponse response(Role role){return response(role,active(List.of(role)).getOrDefault(role.getId(),List.of()));}
    private RoleResponse response(Role role,List<UserRoleAssignment> scoped){LinkedHashSet<String> users=role.getUsers()==null?new LinkedHashSet<>():role.getUsers().stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));scoped.stream().map(UserRoleAssignment::getUserId).filter(Objects::nonNull).forEach(users::add);List<String> types=scoped.stream().map(UserRoleAssignment::getScopeType).filter(Objects::nonNull).distinct().sorted().toList();boolean system=Boolean.TRUE.equals(role.getIsDefault())||(role.getName()!=null&&role.getName().startsWith("system."));return new RoleResponse(role.getId(),role.getName(),role.getDescription(),role.getPermissions()==null?List.of():role.getPermissions().stream().map(Permission::getName).toList(),Boolean.TRUE.equals(role.getIsDefault()),role.getCreatedAt(),role.getUpdatedAt(),users.size(),types,system,"ACTIVE",role.getUpdatedBy());}
}
