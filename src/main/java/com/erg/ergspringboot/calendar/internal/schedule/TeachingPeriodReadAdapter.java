package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public List<TeachingPeriod> periods(String tenantId, LocalDate from, LocalDate to, String status, String schoolId,
            String teacherUserId) {
        if (!PUBLISHED.equals(status) && !CANCELLED.equals(status)) {
            throw new IllegalArgumentException("status must be PUBLISHED or CANCELLED");
        }
        // CANCELLED: only lessons that fell through for the teacher; superseded rows (REPLACED, DRAFT_REMOVED) and
        // pre-V158 rows with no reason were never a lesson the teacher should see as cancelled.
        String onlyReal = CANCELLED.equals(status)
                ? " and e.cancel_reason in ('HOLIDAY', 'ADMIN_CANCEL', 'BATCH_CANCELLED')"
                : "";
        String narrowed = (schoolId == null ? "" : " and e.school_id = :school")
                + (teacherUserId == null ? "" : " and t.teacher_user_id = :teacher");
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
                """ + onlyReal + narrowed + " " + """
                order by e.start_at, c.name, t.teacher_user_id
                """, new MapSqlParameterSource("tenant", tenantId).addValue("status", status)
                        .addValue("school", schoolId).addValue("teacher", teacherUserId)
                        .addValue("from", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
                        .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant())),
                (rs, i) -> new TeachingPeriod(rs.getString("id"), rs.getString("teacher_user_id"),
                        rs.getString("teacher_name"), rs.getString("role"), rs.getString("academic_class_id"),
                        rs.getString("class_name"), rs.getString("subject_id"), rs.getString("subject_name"),
                        rs.getString("school_id"), rs.getString("school_name"), rs.getString("level_id"),
                        rs.getString("level_name"), rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(), rs.getInt("period_start"), rs.getInt("period_count")));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> teacherIds(String tenantId, LocalDate from, LocalDate to) {
        return new HashSet<>(jdbc.queryForList("""
                select distinct t.teacher_user_id
                from teaching_schedule_events e
                join teaching_schedule_event_teachers t on t.event_id = e.id and t.tenant_id = e.tenant_id
                where e.tenant_id = :tenant and e.status = 'PUBLISHED' and e.start_at >= :from and e.start_at < :to
                """, new MapSqlParameterSource("tenant", tenantId)
                        .addValue("from", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
                        .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant())), String.class));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<TeacherPeriod>> teacherPeriods(String tenantId, Collection<String> teacherUserIds,
            LocalDate from, LocalDate to) {
        Map<String, List<TeacherPeriod>> result = new LinkedHashMap<>();
        if (teacherUserIds.isEmpty()) return result;
        // same overlap rule as CalendarReadPort.teacherPeriods: the event touches [from 00:00, to + 1 day 00:00) Vietnam time
        jdbc.query("""
                select t.teacher_user_id, t.role, e.id, e.start_at, e.end_at, c.name class_name, s.name subject_name,
                       e.room_name, e.academic_class_id, e.subject_id, e.school_id, e.level_id, e.period_start, e.period_count
                from teaching_schedule_events e
                join teaching_schedule_event_teachers t on t.event_id = e.id and t.tenant_id = e.tenant_id
                left join academic_classes c on c.id = e.academic_class_id
                left join curriculum_subjects s on s.id = e.subject_id
                where e.tenant_id = :tenant and e.status = 'PUBLISHED' and t.teacher_user_id in (:teachers)
                  and e.start_at < :to and e.end_at > :from
                order by e.start_at, e.id
                """, new MapSqlParameterSource("tenant", tenantId).addValue("teachers", teacherUserIds)
                        .addValue("from", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
                        .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant())),
                rs -> {
                    result.computeIfAbsent(rs.getString("teacher_user_id"), k -> new java.util.ArrayList<>())
                            .add(new TeacherPeriod(rs.getTimestamp("start_at").toInstant(),
                                    rs.getTimestamp("end_at").toInstant(), rs.getString("class_name"),
                                    rs.getString("subject_name"), rs.getString("room_name"), rs.getString("id"),
                                    rs.getString("academic_class_id"), rs.getString("subject_id"),
                                    rs.getString("school_id"), rs.getString("level_id"), rs.getInt("period_start"),
                                    rs.getInt("period_count"), rs.getString("role")));
                });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PeriodSlice periodSlice(String tenantId, LocalDate from, LocalDate to, String role, String schoolId,
            String teacherUserId, int offset, int limit) {
        String narrowed = (schoolId == null ? "" : " and e.school_id = :school")
                + (teacherUserId == null ? "" : " and t.teacher_user_id = :teacher");
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenantId).addValue("role", role)
                .addValue("school", schoolId).addValue("teacher", teacherUserId)
                .addValue("from", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
                .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant()))
                .addValue("offset", offset).addValue("limit", limit);
        // generate_series spreads an event over its periods like TeachingSheets.expand; "C" collation = Java String order
        String periods = """
                with p as (
                    select e.id, t.teacher_user_id, coalesce(u.full_name, t.display_name_snapshot, '') teacher_name, t.role,
                           e.academic_class_id, coalesce(c.name, '') class_name, e.subject_id, coalesce(s.name, '') subject_name,
                           e.school_id, coalesce(sc.name, '') school_name, e.level_id, coalesce(l.name, '') level_name,
                           e.start_at, e.end_at, e.period_start, e.period_count,
                           greatest(1, e.period_count) n,
                           floor(extract(epoch from (e.end_at - e.start_at)) * 1000)::bigint / greatest(1, e.period_count) each_ms
                    from teaching_schedule_events e
                    join teaching_schedule_event_teachers t on t.event_id = e.id and t.tenant_id = e.tenant_id
                    left join users u on u.id = t.teacher_user_id
                    left join academic_classes c on c.id = e.academic_class_id
                    left join curriculum_subjects s on s.id = e.subject_id
                    left join education_units sc on sc.id = e.school_id
                    left join curriculum_levels l on l.id = e.level_id
                    where e.tenant_id = :tenant and e.status = 'PUBLISHED' and t.role = :role
                      and e.start_at >= :from and e.start_at < :to
                """ + narrowed + """
                ), x as (
                    select p.*, k, p.start_at + (p.each_ms * k)::double precision * interval '1 millisecond' tiet_from
                    from p cross join lateral generate_series(0, p.n - 1) k
                )
                """;
        int[] total = {-1};
        List<PeriodRow> rows = jdbc.query(periods + """
                select x.*, count(*) over () total from x
                order by tiet_from, school_name collate "C", class_name collate "C", period_start + k, id, teacher_user_id
                offset :offset limit :limit
                """, params, (rs, i) -> {
                    Instant start = rs.getTimestamp("tiet_from").toInstant();
                    TeachingPeriod event = new TeachingPeriod(rs.getString("id"), rs.getString("teacher_user_id"),
                            rs.getString("teacher_name"), rs.getString("role"), rs.getString("academic_class_id"),
                            rs.getString("class_name"), rs.getString("subject_id"), rs.getString("subject_name"),
                            rs.getString("school_id"), rs.getString("school_name"), rs.getString("level_id"),
                            rs.getString("level_name"), rs.getTimestamp("start_at").toInstant(),
                            rs.getTimestamp("end_at").toInstant(), rs.getInt("period_start"), rs.getInt("period_count"));
                    total[0] = rs.getInt("total");
                    return new PeriodRow(event, rs.getInt("period_start") + rs.getInt("k"), start,
                            start.plusMillis(rs.getLong("each_ms")));
                });
        if (total[0] < 0) total[0] = offset == 0 ? 0 : totalOf(periods, params); // a page past the end still reports the total
        return new PeriodSlice(rows, total[0]);
    }

    private int totalOf(String periods, MapSqlParameterSource params) {
        Integer total = jdbc.queryForObject(periods + "select count(*) from x", params, Integer.class);
        return total == null ? 0 : total;
    }
}
