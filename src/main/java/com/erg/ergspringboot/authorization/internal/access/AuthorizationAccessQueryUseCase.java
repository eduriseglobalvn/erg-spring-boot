package com.erg.ergspringboot.authorization.internal.access;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.*;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped authorization projections and simulation queries. */
@Service
public class AuthorizationAccessQueryUseCase {
    private final UserRepository users; private final UserPermissionOverrideRepository overrides;
    public AuthorizationAccessQueryUseCase(UserRepository users,UserPermissionOverrideRepository overrides){this.users=users;this.overrides=overrides;}
    @Transactional(readOnly=true) public List<PermissionOverrideResponse> listOverrides(String userId,String tenant){scoped(userId,tenant);return overrides.findByUserId(userId).stream().map(this::override).toList();}
    @Transactional(readOnly=true) public EffectivePermissionsResponse effective(String userId,String tenant){User user=users.findWithDetailsByIdAndTenantId(userId,normalize(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));List<String> role=user.getRoles()==null?List.of():user.getRoles().stream().flatMap(r->r.getPermissions().stream()).map(Permission::getName).distinct().toList();List<UserPermissionOverride> os=overrides.findByUserId(userId);List<String> grants=os.stream().filter(o->"GRANT".equalsIgnoreCase(o.getGrantType())).map(UserPermissionOverride::getPermission).toList();List<String> denies=os.stream().filter(o->"DENY".equalsIgnoreCase(o.getGrantType())).map(UserPermissionOverride::getPermission).toList();List<String> effective=Stream.concat(role.stream(),grants.stream()).filter(p->denies.stream().noneMatch(p::equalsIgnoreCase)).distinct().toList();return new EffectivePermissionsResponse(userId,role,grants,denies,effective);}
    @Transactional(readOnly=true) public FeatureConfigResponse feature(String userId,String tenant){EffectivePermissionsResponse p=effective(userId,tenant);User user=users.findWithDetailsByIdAndTenantId(userId,normalize(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));List<String> roles=user.getRoles()==null?List.of():user.getRoles().stream().map(Role::getName).toList();Map<String,Boolean> features=p.effectivePermissions().stream().collect(java.util.stream.Collectors.toMap(x->x,x->true,(a,b)->a));FeatureAccessResponse access=new FeatureAccessResponse(p.effectivePermissions(),p.effectivePermissions(),p.effectivePermissions());return new FeatureConfigResponse(userId,p.effectivePermissions(),features,access,roles);}
    @Transactional(readOnly=true) public PreviewChangesResponse preview(String userId,PreviewChangesRequest request,String tenant){EffectivePermissionsResponse current=effective(userId,tenant);List<String> simulated=new ArrayList<>(current.effectivePermissions());Object grant=request.changes()==null?null:request.changes().get("grant");if(grant instanceof String p&&!p.isBlank())simulated.add(p);Object deny=request.changes()==null?null:request.changes().get("deny");if(deny instanceof String p&&!p.isBlank())simulated.removeIf(x->x.equalsIgnoreCase(p));return new PreviewChangesResponse(current.effectivePermissions(),request.changes()==null?Map.of():request.changes(),simulated.stream().distinct().toList());}
    private PermissionOverrideResponse override(UserPermissionOverride o){return new PermissionOverrideResponse(o.getId(),o.getUserId(),o.getPermission(),o.getGrantType(),o.getReason(),o.getExpiresAt(),o.getCreatedBy(),o.getCreatedAt());}
    private void scoped(String id,String tenant){users.findByIdAndTenantId(id,normalize(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));}
    private String normalize(String value){return value==null||value.isBlank()?"erg":value.trim();}
}
