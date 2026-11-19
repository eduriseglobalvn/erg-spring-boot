package com.erg.ergspringboot.school.internal.command;

import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What archiving a school locks, and only that on restore (table school_archive_holds, V172).
 * Archive: open classes and enrolled students become ARCHIVED, the students' login accounts INACTIVE
 * with their sessions revoked. Restore puts back the recorded status of each held row that is still
 * in the state archive left it in; anything archived or locked on its own was never held.
 */
@Component
class SchoolArchiveHolds {
    record Counts(int classes, int students, int accounts) {
        Map<String, Object> asMap(String schoolId) {
            return Map.of("schoolId", schoolId, "classes", classes, "students", students, "accounts", accounts);
        }
    }

    private static final String HOLD = "insert into school_archive_holds"
            + " (tenant_id, school_id, entity_type, entity_id, previous_status, held_at) ";
    private static final String HELD = " in (select entity_id from school_archive_holds"
            + " where tenant_id = ? and school_id = ? and entity_type = ? and held_at = ?)";

    private final JdbcTemplate jdbc;
    private final UserActivityRepository activities;

    SchoolArchiveHolds(JdbcTemplate jdbc, UserActivityRepository activities) {
        this.jdbc = jdbc;
        this.activities = activities;
    }

    Counts archive(String tenantId, String schoolId, String actorId) {
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(HOLD + "select tenant_id, school_id, 'CLASS', id, status, ? from academic_classes"
                + " where tenant_id = ? and school_id = ? and upper(status) = 'ACTIVE' on conflict do nothing",
                at, tenantId, schoolId);
        jdbc.update(HOLD + "select tenant_id, school_id, 'STUDENT', id, status, ? from lms_students"
                + " where tenant_id = ? and school_id = ? and upper(status) in ('ACTIVE', 'ENROLLED') on conflict do nothing",
                at, tenantId, schoolId);
        // An account also linked to an open student record in another school (a transfer) stays usable.
        jdbc.update(HOLD + "select distinct u.tenant_id, ?, 'USER', u.id, u.status, ? from users u"
                + " join lms_students s on s.auth_user_id = u.id and s.tenant_id = u.tenant_id"
                + " where s.id" + HELD + " and upper(coalesce(u.status, '')) not in ('BLOCKED', 'BANNED', 'INACTIVE', 'DELETED')"
                + " and not exists (select 1 from lms_students other where other.auth_user_id = u.id"
                + " and other.tenant_id = u.tenant_id and other.school_id <> ?"
                + " and upper(other.status) in ('ACTIVE', 'ENROLLED')) on conflict do nothing",
                schoolId, at, tenantId, schoolId, "STUDENT", at, schoolId);
        Counts counts = new Counts(
                jdbc.update("update academic_classes set status = 'ARCHIVED', record_version = record_version + 1,"
                        + " updated_at = ? where id" + HELD, at, tenantId, schoolId, "CLASS", at),
                jdbc.update("update lms_students set status = 'ARCHIVED', record_version = record_version + 1,"
                        + " updated_at = ? where id" + HELD, at, tenantId, schoolId, "STUDENT", at),
                jdbc.update("update users set status = 'INACTIVE', authz_version = coalesce(authz_version, 1) + 1,"
                        + " updated_at = ? where id" + HELD, at, tenantId, schoolId, "USER", at));
        // Same reason as an admin disabling the account, so the client is told ACCOUNT_DISABLED.
        jdbc.update("update user_sessions set revoked_at = ?, revoked_reason = 'status_changed_inactive'"
                + " where revoked_at is null and user_id" + HELD, at, tenantId, schoolId, "USER", at);
        audit(tenantId, actorId, "SCHOOL_ARCHIVED", counts.asMap(schoolId));
        return counts;
    }

    Counts restore(String tenantId, String schoolId, String actorId) {
        Counts counts = new Counts(
                reopen("academic_classes", "CLASS", "'ARCHIVED'", "record_version = record_version + 1,", tenantId, schoolId),
                reopen("lms_students", "STUDENT", "'ARCHIVED'", "record_version = record_version + 1,", tenantId, schoolId),
                reopen("users", "USER", "'INACTIVE'", "authz_version = coalesce(t.authz_version, 1) + 1,", tenantId, schoolId));
        jdbc.update("delete from school_archive_holds where tenant_id = ? and school_id = ?", tenantId, schoolId);
        audit(tenantId, actorId, "SCHOOL_RESTORED", counts.asMap(schoolId));
        return counts;
    }

    /** Reopens held rows still in the state archive left them in; a row changed since then is left as it is. */
    private int reopen(String table, String type, String archivedStatus, String bump, String tenantId, String schoolId) {
        return jdbc.update("update " + table + " t set status = h.previous_status, " + bump + " updated_at = ?"
                + " from school_archive_holds h where h.entity_id = t.id and h.entity_type = ?"
                + " and h.tenant_id = ? and h.school_id = ? and upper(t.status) = " + archivedStatus,
                OffsetDateTime.now(ZoneOffset.UTC), type, tenantId, schoolId);
    }

    private void audit(String tenantId, String actorId, String action, Map<String, Object> metadata) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setUserId(actorId);
        activity.setTenantId(tenantId);
        activity.setAction(action);
        activity.setDescription("School archive lifecycle");
        activity.setMetadata(metadata);
        activity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        activities.save(activity);
    }
}
