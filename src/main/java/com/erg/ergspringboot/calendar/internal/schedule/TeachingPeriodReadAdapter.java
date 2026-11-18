package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One query for the period sheets: events of a status joined to their teachers and display names. */
@Component
class TeachingPeriodReadAdapter implements TeachingPeriodReadPort {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NamedParameterJdbcTemplate jdbc;

    TeachingPeriodReadAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeachingPeriod> periods(String tenantId, LocalDate from, LocalDate to, String status) {
        if (!PUBLISHED.equals(status) && !CANCELLED.equals(status)) {
            throw new IllegalArgumentException("status must be PUBLISHED or CANCELLED");
        }
        // CANCELLED: only lessons that fell through for the teacher; superseded rows (REPLACED, DRAFT_REMOVED) and
        // pre-V158 rows with no reason were never a lesson the teacher should see as cancelled.
        String onlyReal = CANCELLED.equals(status)
                ? " and e.cancel_reason in ('HOLIDAY', 'ADMIN_CANCEL', 'BATCH_CANCELLED')"
                : "";
        return jdbc.query("""
                select e.id, t.teacher_user_id, coalesce(u.full_name, t.display_name_snapshot, '') teacher_name, t.role,
                       e.academic_class_id, coalesce(c.name, '') class_name, e.subject_id, coalesce(s.name, '') subject_name,
                       e.school_id, coalesce(sc.name, '') school_name, e.level_id, coalesce(l.name, '') level_name,
                       e.start_at, e.end_at, e.period_start, e.period_count
                from teaching_schedule_events e
                join teaching_schedule_event_teachers t on t.event_id = e.id and t.tenant_id = e.tenant_id
                left join users u on u.id = t.teacher_user_id
                left join academic_classes c on c.id = e.academic_class_id
                left join curriculum_subjects s on s.id = e.subject_id
                left join education_units sc on sc.id = e.school_id
                left join curriculum_levels l on l.id = e.level_id
                where e.tenant_id = :tenant and e.status = :status and e.start_at >= :from and e.start_at < :to
                """ + onlyReal + """
                order by e.start_at, c.name, t.teacher_user_id
                """, new MapSqlParameterSource("tenant", tenantId).addValue("status", status)
                        .addValue("from", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
                        .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant())),
                (rs, i) -> new TeachingPeriod(rs.getString("id"), rs.getString("teacher_user_id"),
                        rs.getString("teacher_name"), rs.getString("role"), rs.getString("academic_class_id"),
                        rs.getString("class_name"), rs.getString("subject_id"), rs.getString("subject_name"),
                        rs.getString("school_id"), rs.getString("school_name"), rs.getString("level_id"),
                        rs.getString("level_name"), rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(), rs.getInt("period_start"), rs.getInt("period_count")));
    }
}
