package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Who may send to whom: admins to anyone in the tenant, teachers only to their own classes' students. */
@Service
public class AudienceScopeGuard {

    private final LmsAudienceReadPort lms;

    public AudienceScopeGuard(LmsAudienceReadPort lms) {
        this.lms = lms;
    }

    /** Roles-only form (no permissions); see {@link #assertAllowed(CampaignActor, CampaignAudience)}. */
    public void assertAllowed(String tenantId, String actorId, Set<String> actorRoles, CampaignAudience audience) {
        assertAllowed(new CampaignActor(tenantId, actorId, actorRoles), audience);
    }

    public void assertAllowed(CampaignActor actor, CampaignAudience audience) {
        if (isAdmin(actor)) {
            return;
        }
        if (!isTeacher(actor)) {
            throw denied();
        }
        String tenantId = actor.tenantId();
        String actorId = actor.userId();
        if (audience.wholeTenant() || !audience.schoolIds().isEmpty() || !audience.gradeIds().isEmpty()
                || !audience.roles().isEmpty()) {
            throw denied();
        }
        // Bound the IN lists before any query: same 5,000 rule as the resolver.
        Set<String> people = new HashSet<>(audience.userIds());
        requireWithinLimit(people.size());
        List<String> taught = lms.classIdsTaughtBy(tenantId, actorId);
        if (!taught.containsAll(audience.classIds())) {
            throw denied();
        }
        if (!audience.groupIds().isEmpty()) {
            people.addAll(lms.studentUserIdsInGroups(
                    tenantId, audience.groupIds(), NotificationAudienceResolver.MAX_RECIPIENTS + 1));
        }
        requireWithinLimit(people.size());
        if (!people.isEmpty()
                && !Set.copyOf(lms.studentUserIdsInClassesAmong(tenantId, taught, people)).containsAll(people)) {
            throw denied();
        }
    }

    public boolean isAdmin(Collection<String> actorRoles) {
        return workspacesOf(actorRoles).contains("ADMIN");
    }

    public boolean isTeacher(Collection<String> actorRoles) {
        return workspacesOf(actorRoles).contains("TEACHER");
    }

    /** ADMIN never comes from permissions: roles only (plus the platform-wide "admin" CampaignActor adds). */
    public boolean isAdmin(CampaignActor actor) {
        return isAdmin(actor.roles());
    }

    /** TEACHER follows the platform rule over verified roles + permissions minus denies (fail closed when empty). */
    public boolean isTeacher(CampaignActor actor) {
        return WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                List.copyOf(actor.roles()), actor.permissions(), actor.deniedPermissions()).contains("TEACHER");
    }

    /** Single source of truth for admin/teacher detection; null roles count as none (fail closed). */
    private static List<String> workspacesOf(Collection<String> actorRoles) {
        return WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                actorRoles == null ? List.of() : actorRoles.stream().filter(Objects::nonNull).toList(),
                List.of(), List.of());
    }

    private static void requireWithinLimit(int count) {
        if (count > NotificationAudienceResolver.MAX_RECIPIENTS) {
            throw new AudienceTooLargeException();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Giáo viên chỉ được gửi cho học sinh các lớp mình dạy");
    }
}
