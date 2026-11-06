package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Who acts on a campaign. {@code roles} are the role names AudienceScopeGuard decides on; a verified platform-wide
 * principal (system super admin holding '*') also gets "admin", the mapper's own meaning of that grant.
 */
public record CampaignActor(String tenantId, String userId, Set<String> roles) {

    public static CampaignActor of(ErgPrincipal principal) {
        return new CampaignActor(AuthenticatedTenant.require(principal.tenantId()), principal.userId(),
                guardRoles(principal.roles(), principal.permissions(), principal.deniedPermissions()));
    }

    static Set<String> guardRoles(Collection<String> roles, List<String> permissions, List<String> denied) {
        Set<String> result = new LinkedHashSet<>(roles == null ? List.of() : roles);
        if (WorkspaceCapabilityMapper.isPlatformWide(List.copyOf(result), permissions, denied)) {
            result.add("admin");
        }
        return Set.copyOf(result);
    }
}
