package com.erg.ergspringboot.notifications.internal.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Column-only reads for audiences and the people picker. Resolving an audience or filling the directory needs
 * ids, names and a login status, so these queries return exactly that instead of hydrating Student and User
 * entities (up to ~10 MB per call at the 5,000 cap). The roster vocabulary (active/enrolled, login present)
 * mirrors {@code StudentRepository.findAudienceStudents*}; a missing user status counts as active.
 */
@Repository
public class AudienceAccountQueries {

    private static final int CHUNK = 2_000;
    private static final String ACTIVE_STUDENT = """
            s.tenant_id = :tenantId and s.auth_user_id is not null
              and (s.status is null or lower(s.status) in ('active', 'enrolled'))
            """;
    private static final String ACTIVE_LOGIN = "(u.status is null or lower(u.status) = 'active')";

    /** A student on the roll with a login, as the picker shows it. */
    public record StudentRow(String userId, String fullName, String studentCode, String email, String phone, String className) {}

    private final JdbcClient jdbc;

    public AudienceAccountQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The subset of {@code userIds} whose login account exists and is active, in no particular order. */
    public Set<String> activeUserIds(String tenantId, Collection<String> userIds) {
        Set<String> active = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>(userIds);
        for (int from = 0; from < ids.size(); from += CHUNK) {
            active.addAll(jdbc.sql("select u.id from users u where u.tenant_id = :tenantId and u.id in (:ids) and " + ACTIVE_LOGIN)
                    .param("tenantId", tenantId).param("ids", ids.subList(from, Math.min(ids.size(), from + CHUNK)))
                    .query(String.class).list());
        }
        return active;
    }

    /** Distinct active students with an active login; {@code classIds == null} means the whole tenant. */
    public long countActiveStudents(String tenantId, Collection<String> classIds) {
        if (classIds != null && classIds.isEmpty()) {
            return 0;
        }
        JdbcClient.StatementSpec sql = jdbc.sql("""
                select count(distinct s.auth_user_id) from lms_students s
                join users u on u.id = s.auth_user_id and u.tenant_id = s.tenant_id
                """ + " where " + ACTIVE_STUDENT + " and " + ACTIVE_LOGIN + (classIds == null ? "" : " and s.academic_class_id in (:classIds)"))
                .param("tenantId", tenantId);
        return (classIds == null ? sql : sql.param("classIds", classIds)).query(Long.class).single();
    }

    /** Login ids of every active student of the tenant (name order), at most {@code limit}. */
    public List<String> activeStudentUserIds(String tenantId, int limit) {
        return jdbc.sql("""
                select s.auth_user_id from lms_students s
                join users u on u.id = s.auth_user_id and u.tenant_id = s.tenant_id
                """ + " where " + ACTIVE_STUDENT + " and " + ACTIVE_LOGIN + " order by s.full_name asc, s.id asc limit :limit")
                .param("tenantId", tenantId).param("limit", limit).query(String.class).list();
    }

    /** Students on the roll with a login (the login's own status is checked per match), by name, at most {@code limit}. */
    public List<StudentRow> studentDirectory(String tenantId, Collection<String> classIds, int limit) {
        if (classIds != null && classIds.isEmpty()) {
            return List.of();
        }
        JdbcClient.StatementSpec sql = jdbc.sql("""
                select s.auth_user_id, s.full_name, s.student_code, s.email, s.phone, c.name as class_name
                from lms_students s
                left join academic_classes c on c.id = s.academic_class_id and c.tenant_id = s.tenant_id
                """ + " where " + ACTIVE_STUDENT + (classIds == null ? "" : " and s.academic_class_id in (:classIds)")
                + " order by s.full_name asc, s.id asc limit :limit")
                .param("tenantId", tenantId).param("limit", limit);
        return (classIds == null ? sql : sql.param("classIds", classIds))
                .query((row, n) -> new StudentRow(row.getString("auth_user_id"), row.getString("full_name"),
                        row.getString("student_code"), row.getString("email"), row.getString("phone"), row.getString("class_name")))
                .list();
    }
}
