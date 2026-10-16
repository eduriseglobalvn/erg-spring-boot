package com.erg.ergspringboot.authorization.internal.access;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.*;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import com.erg.ergspringboot.users.UserAuthorizationProjection;
import com.erg.ergspringboot.users.UserPermissionOverrideProjection;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped authorization projections and simulation queries. */
@Service
public class AuthorizationAccessQueryUseCase {
    private final UserAuthorizationPort accounts;
    public AuthorizationAccessQueryUseCase(UserAuthorizationPort accounts){this.accounts=accounts;}
    @Transactional(readOnly=true) public List<PermissionOverrideResponse> listOverrides(String userId,String tenant){scoped(userId,tenant);return accounts.findOverrides(userId,tenant).stream().map(this::override).toList();}
    @Transactional(readOnly=true) public EffectivePermissionsResponse effective(String userId,String tenant){UserAuthorizationProjection snapshot=scoped(userId,tenant);return new EffectivePermissionsResponse(userId,snapshot.rolePermissions(),snapshot.grantedPermissions(),snapshot.deniedPermissions(),snapshot.effectivePermissions());}
    @Transactional(readOnly=true) public FeatureConfigResponse feature(String userId,String tenant){UserAuthorizationProjection snapshot=scoped(userId,tenant);List<String> permissions=snapshot.effectivePermissions();Map<String,Boolean> features=permissions.stream().collect(java.util.stream.Collectors.toMap(x->x,x->true,(a,b)->a));FeatureAccessResponse access=new FeatureAccessResponse(permissions,permissions,permissions);return new FeatureConfigResponse(userId,permissions,features,access,snapshot.roleNames());}
    @Transactional(readOnly=true) public PreviewChangesResponse preview(String userId,PreviewChangesRequest request,String tenant){EffectivePermissionsResponse current=effective(userId,tenant);List<String> simulated=new ArrayList<>(current.effectivePermissions());Object grant=request.changes()==null?null:request.changes().get("grant");if(grant instanceof String p&&!p.isBlank())simulated.add(p);Object deny=request.changes()==null?null:request.changes().get("deny");if(deny instanceof String p&&!p.isBlank())simulated.removeIf(x->x.equalsIgnoreCase(p));return new PreviewChangesResponse(current.effectivePermissions(),request.changes()==null?Map.of():request.changes(),simulated.stream().distinct().toList());}
    private PermissionOverrideResponse override(UserPermissionOverrideProjection o){return new PermissionOverrideResponse(o.id(),o.userId(),o.permission(),o.grantType(),o.reason(),o.expiresAt(),o.createdBy(),o.createdAt());}
    private UserAuthorizationProjection scoped(String id,String tenant){return accounts.find(id,normalize(tenant)).orElseThrow(()->new ResourceNotFoundException("user not found"));}
    private String normalize(String value){return value==null||value.isBlank()?"erg":value.trim();}
}
