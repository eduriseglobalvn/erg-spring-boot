package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
import com.erg.ergspringboot.users.UserAuthorizationPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Who may send to whom: tenant-wide admins to anyone in the tenant; unit-scoped admins (holders of notifications.manage
 * without the wildcard) only to schools, classes and students of the education units they manage;
 * teachers only to their own classes' students.
 */
@Service
public class AudienceScopeGuard {

    private static final String NOTIFICATIONS_MANAGE = "notifications.manage";

    private final LmsAudienceReadPort lms;
    private final UserAuthorizationPort authorizations;

    public AudienceScopeGuard(LmsAudienceReadPort lms, UserAuthorizationPort authorizations) {
        this.lms = lms;
        this.authorizations = authorizations;
    }

    /** Roles-only form (no permissions); see {@link #assertAllowed(CampaignActor, CampaignAudience)}. */
    public void assertAllowed(String tenantId, String actorId, Set<String> actorRoles, CampaignAudience audience) {
        assertAllowed(new CampaignActor(tenantId, actorId, actorRoles), audience);
    }

    public void assertAllowed(CampaignActor actor, CampaignAudience audience) {
        if (isAdmin(actor)) {
            return;
        }
        if (isUnitAdmin(actor)) {
            // Grades and staff roles resolve tenant-wide, so a unit-scoped admin may not use them (fail closed).
            if (audience.wholeTenant() || !audience.gradeIds().isEmpty() || !audience.roles().isEmpty()) {
                throw unitDenied();
            }
            List<String> units = managedUnitIds(actor);
            if (!units.containsAll(audience.schoolIds())) {
                throw unitDenied();
            }
            requireWithinClasses(actor.tenantId(), audience, classesOf(actor.tenantId(), units), unitDenied());
            return;
        }
        if (!isTeacher(actor)) {
            throw denied();
        }
        if (audience.wholeTenant() || !audience.schoolIds().isEmpty() || !audience.gradeIds().isEmpty()
                || !audience.roles().isEmpty()) {
            throw denied();
        }
        requireWithinClasses(actor.tenantId(), audience, lms.classIdsTaughtBy(actor.tenantId(), actor.userId()),
                denied());
    }

    /** Explicit classes must be among {@code allowed}; every named person and group member a student of one. */
    private void requireWithinClasses(
            String tenantId, CampaignAudience audience, List<String> allowed, AccessDeniedException denial) {
        // Bound the IN lists before any query: same 5,000 rule as the resolver.
        Set<String> people = new HashSet<>(audience.userIds());
        requireWithinLimit(people.size());
        if (!allowed.containsAll(audience.classIds())) {
            throw denial;
        }
        if (!audience.groupIds().isEmpty()) {
            people.addAll(lms.studentUserIdsInGroups(
                    tenantId, audience.groupIds(), NotificationAudienceResolver.MAX_RECIPIENTS + 1));
        }
        requireWithinLimit(people.size());
        if (!people.isEmpty()
                && !Set.copyOf(lms.studentUserIdsInClassesAmong(tenantId, allowed, people)).containsAll(people)) {
            throw denial;
        }
    }

    /** Tenant-wide: only the platform-wide principal (the wildcard, held by the super admin alone). */
    public boolean isAdmin(CampaignActor actor) {
        return WorkspaceCapabilityMapper.isPlatformWide(
                List.copyOf(actor.roles()), actor.permissions(), actor.deniedPermissions());
    }

    /** Unit-scoped admin: {@code notifications.manage} without the tenant-wide wildcard; reach comes from their units. */
    public boolean isUnitAdmin(CampaignActor actor) {
        return !isAdmin(actor) && holds(actor, NOTIFICATIONS_MANAGE);
    }

    /** Every class of the units a unit-scoped admin manages; empty for anyone else. */
    public List<String> managedClassIds(CampaignActor actor) {
        return isUnitAdmin(actor) ? classesOf(actor.tenantId(), managedUnitIds(actor)) : List.of();
    }

    /** TEACHER follows the platform rule over verified permissions minus denies (fail closed when empty). */
    public boolean isTeacher(CampaignActor actor) {
        return WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                List.copyOf(actor.roles()), actor.permissions(), actor.deniedPermissions()).contains("TEACHER");
    }

    private static boolean holds(CampaignActor actor, String permission) {
        return actor.permissions().contains(permission) && !actor.deniedPermissions().contains(permission);
    }

    private List<String> managedUnitIds(CampaignActor actor) {
        return authorizations.managedEducationUnitIds(actor.userId(), actor.tenantId());
    }

    private List<String> classesOf(String tenantId, List<String> unitIds) {
        return unitIds.isEmpty() ? List.of() : List.copyOf(lms.classIdsOf(tenantId, unitIds, List.of()));
    }

    private static void requireWithinLimit(int count) {
        if (count > NotificationAudienceResolver.MAX_RECIPIENTS) {
            throw new AudienceTooLargeException();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Giáo viên chỉ được gửi cho học sinh các lớp mình dạy");
    }

    private static AccessDeniedException unitDenied() {
        return new AccessDeniedException("Chỉ được gửi cho trường, lớp và học sinh thuộc đơn vị bạn quản lý");
    }
}
