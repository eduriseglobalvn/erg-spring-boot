package com.erg.ergspringboot.authorization.internal.catalog;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.model.authorization.*;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.authorization.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erg.ergspringboot.exception.ResourceNotFoundException;

/** Read-only permission catalog use cases; persistence stays behind this module slice. */
@Service
public class AuthorizationPermissionCatalogUseCase {
    private final PermissionRepository permissions; private final RoleRepository roles;
    private PermissionGroupRepository groups; private PermissionBindingRepository bindings;
    private final UserPermissionOverrideRepository overrides;
    public AuthorizationPermissionCatalogUseCase(PermissionRepository permissions,RoleRepository roles,UserPermissionOverrideRepository overrides){this.permissions=permissions;this.roles=roles;this.overrides=overrides;}
    public void setPermissionGroupRepository(PermissionGroupRepository value){this.groups=value;} public void setPermissionBindingRepository(PermissionBindingRepository value){this.bindings=value;}
    @Transactional(readOnly=true) @Cacheable(cacheNames="authorizationPermissions",key="'all'") public List<PermissionResponse> list(){return map(permissions.findTop500ByOrderByNameAsc());}
    @Transactional(readOnly=true) public PermissionPageResponse search(Integer page,Integer limit,String search,String group,String portal,String status,Boolean used){int p=page==null?0:Math.max(0,page);int l=limit==null?25:Math.max(1,Math.min(100,limit));Page<Permission> result=permissions.searchCatalog(trim(search),trim(group),trim(portal),trim(status),used,PageRequest.of(p,l,Sort.by(Sort.Direction.ASC,"name")));return new PermissionPageResponse(map(result.getContent()),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());}
    @Transactional(readOnly=true) public PermissionResponse get(String id){Permission p=permissions.findById(id).orElseGet(()->permissions.findByNameInIgnoreCase(List.of(id)).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("permission not found")));return map(List.of(p)).getFirst();}
    @Transactional(readOnly=true) @Cacheable(cacheNames="authorizationPermissions",key="'groups'") public List<PermissionGroupResponse> groups(){if(this.groups==null)throw new IllegalStateException("permission group repository is not configured");List<PermissionGroup> gs=this.groups.findAllByOrderByOrderAscNameAsc();Map<String,List<Permission>> by=permissions.findByGroupNameInOrderByGroupNameAscNameAsc(gs.stream().map(PermissionGroup::getName).toList()).stream().collect(Collectors.groupingBy(Permission::getGroupName,LinkedHashMap::new,Collectors.toList()));Map<String,PermissionResponse> mapped=map(by.values().stream().flatMap(List::stream).toList()).stream().collect(Collectors.toMap(PermissionResponse::id,x->x,(a,b)->a,LinkedHashMap::new));return gs.stream().map(g->new PermissionGroupResponse(g.getId(),g.getName(),g.getLabel(),g.getOrder()==null?0:g.getOrder(),by.getOrDefault(g.getName(),List.of()).stream().map(p->mapped.get(p.getId())).filter(Objects::nonNull).toList())).toList();}
    private List<PermissionResponse> map(List<Permission> values){if(values.isEmpty())return List.of();List<String> ids=values.stream().map(Permission::getId).toList();List<String> names=values.stream().map(Permission::getName).toList();Map<String,List<PermissionBinding>> bs=bindings==null?Map.of():bindings.findByPermissionIdInOrderByPermissionIdAscSurfaceTypeAscSurfaceIdentifierAsc(ids).stream().collect(Collectors.groupingBy(PermissionBinding::getPermissionId,LinkedHashMap::new,Collectors.toList()));Map<String,List<Role>> rs=roles.findAllWithPermissionsAndUsersByPermissionIds(ids).stream().flatMap(r->r.getPermissions().stream().filter(p->ids.contains(p.getId())).map(p->Map.entry(p.getId(),r))).collect(Collectors.groupingBy(Map.Entry::getKey,LinkedHashMap::new,Collectors.mapping(Map.Entry::getValue,Collectors.toList())));Map<String,List<UserPermissionOverride>> os=overrides.findByPermissionIn(names).stream().collect(Collectors.groupingBy(UserPermissionOverride::getPermission,LinkedHashMap::new,Collectors.toList()));return values.stream().map(p->response(p,rs.getOrDefault(p.getId(),List.of()),bs.getOrDefault(p.getId(),List.of()),os.getOrDefault(p.getName(),List.of()))).toList();}
    private PermissionResponse response(Permission p,List<Role> rs,List<PermissionBinding> bs,List<UserPermissionOverride> os){List<PermissionBindingResponse> br=bs.stream().sorted(Comparator.comparing(PermissionBinding::getSurfaceType,Comparator.nullsLast(String::compareTo)).thenComparing(PermissionBinding::getSurfaceIdentifier,Comparator.nullsLast(String::compareTo))).map(b->new PermissionBindingResponse(b.getId(),b.getSurfaceType(),b.getSurfaceIdentifier(),b.getDescription(),Boolean.TRUE.equals(b.getEnforced()))).toList();Set<String> users=rs.stream().filter(Objects::nonNull).flatMap(r->r.getUsers()==null?Stream.empty():r.getUsers().stream()).filter(Objects::nonNull).map(User::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));os.stream().map(UserPermissionOverride::getUserId).filter(Objects::nonNull).forEach(users::add);return new PermissionResponse(p.getId(),p.getName(),p.getGroupName(),p.getLabel(),p.getDescription(),p.getResource(),p.getAction(),p.getPortal(),p.getSupportedScope(),p.getStatus(),Boolean.TRUE.equals(p.getIsSystem()),p.getDeprecatedAt(),br.stream().anyMatch(PermissionBindingResponse::enforced)||!rs.isEmpty()||!os.isEmpty(),rs.size(),users.size(),rs.stream().map(Role::getName).filter(Objects::nonNull).sorted().toList(),br);}
    private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
}
