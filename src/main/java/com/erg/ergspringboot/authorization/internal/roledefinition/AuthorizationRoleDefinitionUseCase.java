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
    @Transactional(readOnly=true) @Cacheable(cacheNames="authorizationRoles",key="'all'") public List<RoleResponse> list(){List<Role> all=roles.findAllWithPermissionsAndUsers();Map<String,List<String>> by=scopeTypes(all);Map<String,Integer> counts=userCounts();return all.stream().filter(r->!"system.god_mode".equalsIgnoreCase(r.getName())).map(r->response(r,by.getOrDefault(r.getId(),List.of()),counts.getOrDefault(r.getId(),0))).toList();}
    @Transactional(readOnly=true) public RoleResponse get(String id){Role role=find(id);if("system.god_mode".equalsIgnoreCase(role.getName()))throw new ResourceNotFoundException("role not found");return response(role);}
    private Role find(String id){return roles.findById(id).or(()->roles.findByNameIgnoreCase(id)).orElseThrow(()->new ResourceNotFoundException("role not found"));}
    private Map<String,List<String>> scopeTypes(List<Role> values){Map<String,List<String>> types=new HashMap<>();if(assignments==null||values.isEmpty())return types;for(Object[] row:assignments.findActiveScopeTypes(values.stream().map(Role::getId).filter(Objects::nonNull).toList(),OffsetDateTime.now(ZoneOffset.UTC)))types.computeIfAbsent((String)row[0],k->new ArrayList<>()).add((String)row[1]);types.values().forEach(Collections::sort);return types;}
    private RoleResponse response(Role role){return response(role,scopeTypes(List.of(role)).getOrDefault(role.getId(),List.of()),userCounts().getOrDefault(role.getId(),0));}
    /** One grouped query for every role (direct users plus active scoped assignments) instead of loading Role.getUsers(). */
    private Map<String,Integer> userCounts(){Map<String,Integer> counts=new HashMap<>();for(Object[] row:roles.countUsersByRole(OffsetDateTime.now(ZoneOffset.UTC)))counts.put((String)row[0],((Number)row[1]).intValue());return counts;}
    private RoleResponse response(Role role,List<String> types,int userCount){boolean system=Boolean.TRUE.equals(role.getIsDefault())||(role.getName()!=null&&role.getName().startsWith("system."));return new RoleResponse(role.getId(),role.getName(),role.getDescription(),role.getPermissions()==null?List.of():role.getPermissions().stream().map(Permission::getName).toList(),Boolean.TRUE.equals(role.getIsDefault()),role.getCreatedAt(),role.getUpdatedAt(),userCount,types,system,"ACTIVE",role.getUpdatedBy());}
}
