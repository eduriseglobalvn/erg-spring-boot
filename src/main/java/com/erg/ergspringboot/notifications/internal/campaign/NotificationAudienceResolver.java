package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Turns a {@link CampaignAudience} into the distinct people it reaches, reading other modules only via their ports. */
@Service
public class NotificationAudienceResolver {

    public static final int MAX_RECIPIENTS = 5_000;
    private static final int FETCH_LIMIT = MAX_RECIPIENTS + 1;
    private static final Pageable FETCH_PAGE = PageRequest.ofSize(FETCH_LIMIT);

    private enum Kind { TEACHER, STUDENT, STAFF, PICKED }

    private final LmsAudienceReadPort lms;
    private final UserDirectoryPort users;

    public NotificationAudienceResolver(LmsAudienceReadPort lms, UserDirectoryPort users) {
        this.lms = lms;
        this.users = users;
    }

    public ResolvedAudience resolve(String tenantId, CampaignAudience audience) {
        Set<String> picked = new LinkedHashSet<>(audience.userIds());
        if (picked.size() > MAX_RECIPIENTS) {
            throw new AudienceTooLargeException();
        }
        Map<String, Kind> reached = new LinkedHashMap<>();
        resolveStudents(tenantId, audience, reached);
        resolveTeachers(tenantId, audience, reached);
        if (audience.wholeTenant() || audience.roles().contains("STAFF")) {
            add(reached, Kind.STAFF, users.findActiveStaffUserIds(tenantId, FETCH_PAGE));
        }
        int duplicates = addPicked(tenantId, picked, reached);
        int pickedCount = count(reached, Kind.PICKED) + duplicates;
        return new ResolvedAudience(List.copyOf(reached.keySet()), new AudienceBreakdown(
                reached.size(), count(reached, Kind.TEACHER), count(reached, Kind.STUDENT),
                count(reached, Kind.STAFF), pickedCount, duplicates));
    }

    private void resolveStudents(String tenantId, CampaignAudience audience, Map<String, Kind> reached) {
        Set<String> classIds = new LinkedHashSet<>(audience.classIds());
        if (!audience.schoolIds().isEmpty() || !audience.gradeIds().isEmpty()) {
            classIds.addAll(lms.classIdsOf(tenantId, audience.schoolIds(), audience.gradeIds()));
        }
        if (!classIds.isEmpty()) {
            add(reached, Kind.STUDENT,
                    withActiveLogin(tenantId, lms.studentUserIdsInClasses(tenantId, List.copyOf(classIds), FETCH_LIMIT)));
        }
        if (!audience.groupIds().isEmpty()) {
            add(reached, Kind.STUDENT,
                    withActiveLogin(tenantId, lms.studentUserIdsInGroups(tenantId, audience.groupIds(), FETCH_LIMIT)));
        }
        if (audience.wholeTenant() || audience.roles().contains("STUDENT")) {
            add(reached, Kind.STUDENT, withActiveLogin(tenantId, lms.allStudentUserIds(tenantId, FETCH_LIMIT)));
        }
    }

    private void resolveTeachers(String tenantId, CampaignAudience audience, Map<String, Kind> reached) {
        List<UserAccountProjection> teachers = List.of();
        if (audience.wholeTenant() || audience.roles().contains("TEACHER")) {
            teachers = users.findCalendarTeachersByTenant(tenantId, FETCH_PAGE);
        } else if (!audience.schoolIds().isEmpty()) {
            teachers = users.findCalendarTeachersByTenantAndSchoolIds(tenantId, audience.schoolIds(), FETCH_PAGE);
        }
        add(reached, Kind.TEACHER, teachers.stream().map(UserAccountProjection::id).toList());
    }

    /** Picked users keep request order; unknown or inactive ids are dropped. Returns how many were already reached. */
    private int addPicked(String tenantId, Set<String> picked, Map<String, Kind> reached) {
        List<String> unknown = picked.stream().filter(id -> !reached.containsKey(id)).toList();
        Set<String> valid = new LinkedHashSet<>();
        valid.addAll(activeAmong(tenantId, unknown));
        int duplicates = 0;
        for (String id : picked) {
            if (reached.containsKey(id)) {
                duplicates++;
            } else if (valid.contains(id)) {
                reached.put(id, Kind.PICKED);
            }
        }
        requireWithinLimit(reached);
        return duplicates;
    }

    /** Drops students whose login account is missing or not active, keeping order. */
    private List<String> withActiveLogin(String tenantId, List<String> studentUserIds) {
        Set<String> active = activeAmong(tenantId, studentUserIds);
        return studentUserIds.stream().filter(active::contains).toList();
    }

    /** A missing status counts as active, like the teacher directory query. */
    private Set<String> activeAmong(String tenantId, List<String> userIds) {
        Set<String> active = new LinkedHashSet<>();
        if (!userIds.isEmpty()) {
            users.findByTenantIdAndIds(tenantId, userIds).stream()
                    .filter(user -> user.status() == null || "ACTIVE".equalsIgnoreCase(user.status()))
                    .forEach(user -> active.add(user.id()));
        }
        return active;
    }

    private static void add(Map<String, Kind> reached, Kind kind, List<String> ids) {
        ids.forEach(id -> reached.putIfAbsent(id, kind));
        requireWithinLimit(reached);
    }

    private static void requireWithinLimit(Map<String, Kind> reached) {
        if (reached.size() > MAX_RECIPIENTS) {
            throw new AudienceTooLargeException();
        }
    }

    private static int count(Map<String, Kind> reached, Kind kind) {
        return (int) reached.values().stream().filter(kind::equals).count();
    }
}
