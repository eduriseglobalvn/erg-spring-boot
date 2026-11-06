package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who acts on a campaign. ADMIN is decided from {@code roles} only; a verified platform-wide principal (system super
 * admin holding '*') also gets "admin", the mapper's own meaning of that grant. TEACHER additionally uses the
 * verified permissions (platform rule), see {@link AudienceScopeGuard#isTeacher(CampaignActor)}.
 */
public record CampaignActor(
        String tenantId, String userId, Set<String> roles, List<String> permissions, List<String> deniedPermissions) {

    public CampaignActor {
        roles = clean(roles).stream().collect(Collectors.toUnmodifiableSet());
        permissions = clean(permissions);
        deniedPermissions = clean(deniedPermissions);
    }

    /** Roles only, no permissions (callers that predate permission-aware teacher detection). */
    public CampaignActor(String tenantId, String userId, Collection<String> roles) {
        this(tenantId, userId, roles == null ? null : new LinkedHashSet<>(clean(roles)), List.of(), List.of());
    }

    public static CampaignActor of(ErgPrincipal principal) {
        return of(AuthenticatedTenant.require(principal.tenantId()), principal.userId(), principal.roles(),
                principal.permissions(), principal.deniedPermissions());
    }

    static CampaignActor of(String tenantId, String userId, Collection<String> roles, List<String> permissions,
            List<String> denied) {
        Set<String> result = new LinkedHashSet<>(clean(roles));
        if (WorkspaceCapabilityMapper.isPlatformWide(List.copyOf(result), permissions, denied)) {
            result.add("admin");
        }
        return new CampaignActor(tenantId, userId, result, permissions, denied);
    }

    /** Nobody: every guard check denies. */
    static CampaignActor none(String tenantId, String userId) {
        return new CampaignActor(tenantId, userId, Set.of(), List.of(), List.of());
    }

    private static List<String> clean(Collection<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
