package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
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

    public void assertAllowed(String tenantId, String actorId, Set<String> actorRoles, CampaignAudience audience) {
        List<String> workspaces = WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                actorRoles == null ? List.of() : actorRoles.stream().filter(Objects::nonNull).toList(),
                List.of(), List.of());
        if (workspaces.contains("ADMIN")) {
            return;
        }
        if (!workspaces.contains("TEACHER")) {
            throw denied();
        }
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

    private static void requireWithinLimit(int count) {
        if (count > NotificationAudienceResolver.MAX_RECIPIENTS) {
            throw new AudienceTooLargeException();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Giáo viên chỉ được gửi cho học sinh các lớp mình dạy");
    }
}
