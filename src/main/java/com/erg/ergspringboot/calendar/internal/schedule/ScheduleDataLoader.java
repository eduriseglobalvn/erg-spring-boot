package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.ClassRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Leave;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.RoomRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.SchoolRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the schedule conflict engine: published events, leave, holidays, rooms. Plain SQL, tenant-scoped. */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class ScheduleDataLoader {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String EVENT_COLUMNS = """
            select e.id, e.batch_id, b.label batch_label, b.status batch_status, b.created_at batch_created_at,
                   (select u.full_name from users u where u.id = b.created_by) created_by_name,
                   (select min(r.name) from user_roles ur join roles r on r.id = ur.role_id
                     where ur.user_id = b.created_by) created_by_role,
                   e.school_id, su.name school_name, e.academic_class_id, c.name class_name, e.room_id,
                   coalesce(rm.name, e.room_name) room_name, s.name subject_name,
                   e.start_at, e.end_at, e.period_start, e.status event_status
            from teaching_schedule_events e
            join teaching_schedule_batches b on b.id = e.batch_id
            left join education_units su on su.id = e.school_id
            left join academic_classes c on c.id = e.academic_class_id
            left join lms_subjects s on s.id = e.subject_id
            left join teaching_schedule_rooms rm on rm.id = e.room_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ScheduleDataLoader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * PUBLISHED events starting in [from, to) that touch one of the given classes, rooms or teachers. Drafts of any
     * batch and cancelled events are never returned, so another batch's draft cannot cause a conflict.
     */
    List<Occurrence> loadPublished(String tenantId, OffsetDateTime from, OffsetDateTime to, Collection<String> classIds,
            Collection<String> roomIds, Collection<String> teacherIds, Collection<String> excludeEventIds) {
        if (classIds.isEmpty() && roomIds.isEmpty() && teacherIds.isEmpty()) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenantId)
                .addValue("from", from).addValue("to", to)
                .addValue("classes", orSentinel(classIds)).addValue("rooms", orSentinel(roomIds))
                .addValue("teachers", orSentinel(teacherIds));
        List<Occurrence> rows = jdbc.query(EVENT_COLUMNS + """
                where e.tenant_id = :tenant and e.status = 'PUBLISHED'
                  and e.start_at >= :from and e.start_at < :to
                  and (e.academic_class_id in (:classes) or e.room_id in (:rooms)
                       or exists (select 1 from teaching_schedule_event_teachers t
                                  where t.event_id = e.id and t.teacher_user_id in (:teachers)))
                """, params, (rs, i) -> occurrence(rs, false));
        // excluded in memory: an overview excludes thousands of ids and would blow the JDBC parameter limit in SQL
        return withTeachers(tenantId, rows.stream().filter(o -> !excludeEventIds.contains(o.eventId())).toList(), false, false);
    }

    /**
     * Every non-cancelled event starting in [from, to): PUBLISHED ones of any batch plus DRAFT ones of all batches
     * ({@code allDrafts}) or only of {@code draftBatchId}. {@code schoolIds} null means every school. Occurrences come
     * back as candidates ({@code mine}) keyed by event id, and carry the EVENT status in {@code batchStatus}.
     */
    List<Occurrence> loadWeek(String tenantId, OffsetDateTime from, OffsetDateTime to, Collection<String> schoolIds,
            boolean allDrafts, String draftBatchId) {
        if (schoolIds != null && schoolIds.isEmpty()) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenantId).addValue("from", from)
                .addValue("to", to).addValue("allSchools", schoolIds == null).addValue("schools", orSentinel(schoolIds))
                .addValue("allDrafts", allDrafts).addValue("batch", draftBatchId == null ? "-" : draftBatchId);
        List<Occurrence> rows = jdbc.query(EVENT_COLUMNS + """
                where e.tenant_id = :tenant and e.status <> 'CANCELLED' and e.start_at >= :from and e.start_at < :to
                  and (:allSchools or e.school_id in (:schools))
                  and (e.status = 'PUBLISHED' or :allDrafts or e.batch_id = :batch)
                """, params, (rs, i) -> occurrence(rs, true));
        return withTeachers(tenantId, rows, true, false);
    }

    /** Events of one batch in the given status as candidates; repeats of one grid row share a rowKey. */
    List<Occurrence> loadBatchCandidates(String tenantId, String batchId, String status) {
        List<Occurrence> rows = jdbc.query(EVENT_COLUMNS + " where e.tenant_id = :tenant and e.batch_id = :batch and e.status = :status",
                new MapSqlParameterSource("tenant", tenantId).addValue("batch", batchId).addValue("status", status),
                (rs, i) -> occurrence(rs, false));
        return withTeachers(tenantId, rows, true, true);
    }

    /** The given events (any status) of the tenant as candidates carrying their EVENT status; foreign ids are dropped. */
    List<Occurrence> loadEvents(String tenantId, Collection<String> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        List<Occurrence> rows = jdbc.query(EVENT_COLUMNS + " where e.tenant_id = :tenant and e.id in (:ids)",
                new MapSqlParameterSource("tenant", tenantId).addValue("ids", eventIds), (rs, i) -> occurrence(rs, true));
        return withTeachers(tenantId, rows, true, false);
    }

    Map<String, List<Leave>> loadLeaves(String tenantId, Collection<String> teacherIds, LocalDate from, LocalDate to) {
        if (teacherIds.isEmpty()) return Map.of();
        Map<String, List<Leave>> byUser = new HashMap<>();
        jdbc.query("""
                select user_id, from_date, to_date, part from tk_requests
                where tenant_id = :tenant and type = 'LEAVE' and status = 'APPROVED'
                  and user_id in (:users) and from_date <= :to and to_date >= :from
                """, new MapSqlParameterSource("tenant", tenantId).addValue("users", teacherIds)
                .addValue("from", java.sql.Date.valueOf(from)).addValue("to", java.sql.Date.valueOf(to)), rs -> {
            byUser.computeIfAbsent(rs.getString("user_id"), k -> new ArrayList<>()).add(new Leave(rs.getString("user_id"),
                    rs.getDate("from_date").toLocalDate(), rs.getDate("to_date").toLocalDate(), rs.getString("part")));
        });
        return byUser;
    }

    /** A day off the schedule must skip; {@code kind} is STATE, COMPANY or COMPENSATORY. */
    public record Holiday(String name, String kind) {}

    /**
     * Days off in the range that cancel teaching (KEEP rows are ordinary teaching days). A row without a school
     * applies to every school; an old row with a school only to that school, so {@code schoolIds} are the schools
     * the caller is looking at.
     */
    public Map<LocalDate, Holiday> loadHolidays(String tenantId, LocalDate from, LocalDate to, Collection<String> schoolIds) {
        Map<LocalDate, Holiday> holidays = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenantId)
                .addValue("from", java.sql.Date.valueOf(from)).addValue("to", java.sql.Date.valueOf(to));
        String school = "school_id is null";
        if (schoolIds != null && !schoolIds.isEmpty()) {
            school += " or school_id in (:schools)";
            params.addValue("schools", schoolIds);
        }
        jdbc.query("select holiday_date, name, kind from tk_holidays where tenant_id = :tenant"
                        + " and holiday_date between :from and :to and teaching_action = 'CANCEL' and (" + school + ")"
                        + " order by holiday_date, school_id nulls first",
                params, rs -> {
                    holidays.put(rs.getDate("holiday_date").toLocalDate(),
                            new Holiday(rs.getString("name"), rs.getString("kind")));
                });
        return holidays;
    }

    static final int IN_CHUNK = 5000;

    /** Splits an id collection so one `in (:ids)` never carries more than {@link #IN_CHUNK} parameters. */
    static <T> List<List<T>> chunks(Collection<T> ids) {
        List<T> all = List.copyOf(ids);
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i += IN_CHUNK) result.add(all.subList(i, Math.min(all.size(), i + IN_CHUNK)));
        return result;
    }

    /** id to display text for the given ids of the tenant; {@code table}/{@code column} are code constants, never user input. */
    Map<String, String> names(String tenantId, String table, String column, Collection<String> ids) {
        return names(tenantId, table, column, ids, null);
    }

    /** Same, restricted to one school when {@code schoolId} is given (tables with a school_id column only). */
    Map<String, String> names(String tenantId, String table, String column, Collection<String> ids, String schoolId) {
        Map<String, String> names = new HashMap<>();
        List<String> clean = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        for (List<String> chunk : chunks(clean)) {
            MapSqlParameterSource params = new MapSqlParameterSource("ids", chunk).addValue("tenant", tenantId);
            if (schoolId != null) params.addValue("school", schoolId);
            jdbc.query("select id, " + column + " name from " + table + " where tenant_id = :tenant and id in (:ids)"
                    + (schoolId == null ? "" : " and school_id = :school"), params, rs -> {
                names.put(rs.getString("id"), rs.getString("name"));
            });
        }
        return names;
    }

    /** Of the given event ids, those that exist but are not a DRAFT event of this tenant and batch (id clash / foreign event). */
    List<String> eventIdsOutsideDraft(String tenantId, String batchId, Collection<String> eventIds) {
        List<String> outside = new ArrayList<>();
        for (List<String> chunk : chunks(eventIds)) {
            outside.addAll(jdbc.queryForList("select id from teaching_schedule_events where id in (:ids) and not"
                    + " (tenant_id = :tenant and batch_id = :batch and status = 'DRAFT')",
                    new MapSqlParameterSource("ids", chunk).addValue("tenant", tenantId).addValue("batch", batchId), String.class));
        }
        return outside;
    }

    List<RoomRef> loadRooms(String tenantId, String schoolId) {
        return jdbc.query("select id, name, seat_count from teaching_schedule_rooms"
                        + " where tenant_id = :tenant and school_id = :school and status = 'ACTIVE' order by name",
                new MapSqlParameterSource("tenant", tenantId).addValue("school", schoolId),
                (rs, i) -> new RoomRef(rs.getString("id"), rs.getString("name"), (Integer) rs.getObject("seat_count")));
    }

    /** Schools (education units with classes or periods) of the tenant; {@code scope} null means all of them. */
    List<SchoolRef> loadSchools(String tenantId, Collection<String> scope) {
        if (scope != null && scope.isEmpty()) return List.of();
        return jdbc.query("""
                select u.id, u.name from education_units u
                where u.tenant_id = :tenant and (:all or u.id in (:ids))
                  and (exists (select 1 from academic_classes c where c.tenant_id = :tenant and c.school_id = u.id)
                       or exists (select 1 from teaching_schedule_events e where e.tenant_id = :tenant and e.school_id = u.id))
                order by u.name
                """, new MapSqlParameterSource("tenant", tenantId).addValue("all", scope == null).addValue("ids", orSentinel(scope)),
                (rs, i) -> new SchoolRef(rs.getString("id"), rs.getString("name")));
    }

    /** Active classes of a school; the grade is the first number found in the grade text ("Khối 9" gives 9). */
    List<ClassRef> loadClasses(String tenantId, String schoolId) {
        return jdbc.query("select id, name, grade from academic_classes where tenant_id = :tenant and school_id = :school"
                        + " and coalesce(status, 'ACTIVE') <> 'ARCHIVED' order by name",
                new MapSqlParameterSource("tenant", tenantId).addValue("school", schoolId), (rs, i) -> {
                    java.util.regex.Matcher digits = java.util.regex.Pattern.compile("[0-9]+")
                            .matcher(rs.getString("grade") == null ? "" : rs.getString("grade"));
                    return new ClassRef(rs.getString("id"), rs.getString("name"), digits.find() ? Integer.valueOf(digits.group()) : null);
                });
    }

    /** Published periods of a class in [from, to) per room (null key = no room), to find the room it usually uses. */
    Map<String, Integer> roomUsage(String tenantId, String classId, OffsetDateTime from, OffsetDateTime to) {
        Map<String, Integer> usage = new HashMap<>();
        jdbc.query("select room_id, count(*) n from teaching_schedule_events where tenant_id = :tenant"
                        + " and academic_class_id = :class and status = 'PUBLISHED' and start_at >= :from and start_at < :to"
                        + " group by room_id",
                new MapSqlParameterSource("tenant", tenantId).addValue("class", classId).addValue("from", from)
                        .addValue("to", to), rs -> {
                    usage.put(rs.getString("room_id"), rs.getInt("n"));
                });
        return usage;
    }

    /** Teachers (of those given) that have ever had a non-cancelled event at the school. */
    Set<String> teachersWhoTaughtAt(String tenantId, String schoolId, Collection<String> teacherIds) {
        if (teacherIds.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList("""
                select distinct t.teacher_user_id from teaching_schedule_event_teachers t
                join teaching_schedule_events e on e.id = t.event_id
                where e.tenant_id = :tenant and e.school_id = :school and e.status <> 'CANCELLED'
                  and t.teacher_user_id in (:teachers)
                """, new MapSqlParameterSource("tenant", tenantId).addValue("school", schoolId)
                .addValue("teachers", teacherIds), String.class));
    }

    private List<Occurrence> withTeachers(String tenantId, List<Occurrence> events, boolean mine, boolean contentKey) {
        if (events.isEmpty()) return events;
        Map<String, List<TeacherRef>> teachers = new HashMap<>();
        for (List<String> chunk : chunks(events.stream().map(Occurrence::eventId).toList())) {
            jdbc.query("""
                    select t.event_id, t.teacher_user_id, t.role, coalesce(t.display_name_snapshot, u.full_name) name
                    from teaching_schedule_event_teachers t left join users u on u.id = t.teacher_user_id
                    where t.tenant_id = :tenant and t.event_id in (:ids)
                    """, new MapSqlParameterSource("tenant", tenantId).addValue("ids", chunk), rs -> {
                teachers.computeIfAbsent(rs.getString("event_id"), k -> new ArrayList<>())
                        .add(new TeacherRef(rs.getString("teacher_user_id"), rs.getString("name"), rs.getString("role")));
            });
        }
        return events.stream().map(e -> new Occurrence(e.eventId(), contentKey ? rowKey(e, teachers) : e.eventId(), null, e.batchId(), e.batchLabel(),
                e.batchStatus(), e.schoolId(), e.schoolName(), e.classId(), e.className(), e.roomId(), e.roomName(),
                e.subjectName(), teachers.getOrDefault(e.eventId(), List.of()), e.date(), e.weekday(), e.periodNo(),
                e.start(), e.end(), e.createdByName(), e.createdByRole(), e.createdAt(), mine)).toList();
    }

    private static String rowKey(Occurrence e, Map<String, List<TeacherRef>> teachers) {
        String who = teachers.getOrDefault(e.eventId(), List.of()).stream().map(t -> t.role() + t.id()).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return String.join("|", e.classId(), String.valueOf(e.weekday()), String.valueOf(e.periodNo()), e.roomId() == null ? "" : e.roomId(), who);
    }

    private static Occurrence occurrence(ResultSet rs, boolean eventStatus) throws SQLException {
        OffsetDateTime start = rs.getObject("start_at", OffsetDateTime.class).atZoneSameInstant(VN).toOffsetDateTime();
        OffsetDateTime end = rs.getObject("end_at", OffsetDateTime.class).atZoneSameInstant(VN).toOffsetDateTime();
        return new Occurrence(rs.getString("id"), rs.getString("id"), null, rs.getString("batch_id"),
                rs.getString("batch_label"), rs.getString(eventStatus ? "event_status" : "batch_status"), rs.getString("school_id"),
                rs.getString("school_name"), rs.getString("academic_class_id"), rs.getString("class_name"),
                rs.getString("room_id"), rs.getString("room_name"), rs.getString("subject_name"), List.of(),
                start.toLocalDate(), start.getDayOfWeek().getValue(), rs.getInt("period_start"), start, end,
                rs.getString("created_by_name"), rs.getString("created_by_role"),
                rs.getObject("batch_created_at", OffsetDateTime.class), false);
    }

    private static Collection<String> orSentinel(Collection<String> values) {
        return values == null || values.isEmpty() ? List.of("-") : values;
    }
}
