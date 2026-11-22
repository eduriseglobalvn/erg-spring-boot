package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarHolidayPort;
import com.erg.ergspringboot.calendar.LessonReportCounter;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import com.erg.ergspringboot.calendar.CalendarCacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a day off to PUBLISHED periods (B11). A cancelled period is an ordinary CANCELLED event that remembers the
 * holiday in {@code cancelled_by_holiday_id}, so restoring touches only those and never a period cancelled for
 * another reason. Plain SQL like {@link ScheduleDataLoader}; every statement carries the tenant.
 */
@Service
@Transactional("postgresTransactionManager")
public class ScheduleHolidayService implements CalendarHolidayPort {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ScheduleHolidayService.class);
    private static final String VN_DAY = "(e.start_at at time zone 'Asia/Ho_Chi_Minh')::date";
    private static final java.time.ZoneId VIETNAM = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final ObjectProvider<LessonReportCounter> reports;
    private final Clock clock;
    private final CalendarBatchRepository batches;
    private final ScheduleDataLoader loader;
    private final ScheduleConflictEngine engine;

    public ScheduleHolidayService(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher publisher,
            ObjectProvider<LessonReportCounter> reports, Clock clock, CalendarBatchRepository batches, ScheduleDataLoader loader,
            ScheduleConflictEngine engine) {
        this.batches = batches;
        this.loader = loader;
        this.engine = engine;
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.reports = reports;
        this.clock = clock;
    }

    private record Row(String id, String schoolId, String schoolName, int periods) {
    }

    @Override
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public HolidayImpact pastImpact(String tenantId, String schoolId, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return new HolidayImpact(List.of(), 0, 0, List.of());
        MapSqlParameterSource p = dayParams(tenantId, schoolId, dates).addValue("now", Timestamp.from(clock.instant()));
        Map<String, LocalDate> dayOf = new HashMap<>();
        List<Row> rows = jdbc.query("select e.id, e.school_id, su.name school_name, e.period_count, " + VN_DAY + " d"
                + " from teaching_schedule_events e left join education_units su on su.id = e.school_id"
                + " where e.tenant_id = :tenant and e.status = 'PUBLISHED' and e.start_at < :now"
                + " and " + dayFilter(schoolId) + " order by su.name, e.start_at", p, (rs, i) -> {
                    dayOf.put(rs.getString("id"), rs.getDate("d").toLocalDate());
                    return new Row(rs.getString("id"), rs.getString("school_id"), rs.getString("school_name"),
                            rs.getInt("period_count"));
                });
        if (rows.isEmpty()) return new HolidayImpact(List.of(), 0, 0, List.of());
        LessonReportCounter counter = reports.getIfAvailable();
        Set<String> reported = counter == null ? Set.of() : counter.reported(tenantId, dayOf.keySet());
        Map<String, int[]> bySchool = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        int taught = 0;
        int reportedTotal = 0;
        for (Row r : rows) {
            int[] counts = bySchool.computeIfAbsent(r.schoolId(), k -> new int[2]);
            names.put(r.schoolId(), r.schoolName() == null ? "" : r.schoolName());
            counts[0] += r.periods();
            taught += r.periods();
            if (reported.contains(r.id())) {
                counts[1] += r.periods();
                reportedTotal += r.periods();
            }
        }
        List<SchoolImpact> schools = new ArrayList<>();
        bySchool.forEach((id, c) -> schools.add(new SchoolImpact(id, names.get(id), c[0], c[1])));
        return new HolidayImpact(new ArrayList<>(new TreeSet<>(dayOf.values())), taught, reportedTotal, schools);
    }

    @Override
    @CalendarCacheEvict
    public int cancelPeriods(String tenantId, String schoolId, Collection<LocalDate> dates, String holidayId,
            String holidayName, String actorId) {
        if (dates.isEmpty()) return 0;
        List<String> ids = jdbc.queryForList("select e.id from teaching_schedule_events e where e.tenant_id = :tenant"
                + " and e.status = 'PUBLISHED' and " + dayFilter(schoolId) + " order by e.id for update",
                dayParams(tenantId, schoolId, dates), String.class);
        if (ids.isEmpty()) return 0;
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.update("update teaching_schedule_events set status = 'CANCELLED', cancel_reason = 'HOLIDAY', cancelled_by_holiday_id = :holiday,"
                        + " updated_by = :actor, updated_at = :now, record_version = record_version + 1"
                        + " where tenant_id = :tenant and id in (:ids)",
                new MapSqlParameterSource("tenant", tenantId).addValue("holiday", holidayId)
                        .addValue("actor", actorId).addValue("now", Timestamp.from(now.toInstant())).addValue("ids", ids));
        publisher.publishEvent(new CalendarHolidayCancelled(
                "holiday-cancel:" + holidayId + ":" + now.toInstant().toEpochMilli(),
                tenantId, holidayId, holidayName, ids, actorId, now, 1));
        return ids.size();
    }

    @Override
    @CalendarCacheEvict
    public int restorePeriods(String tenantId, String holidayId, String actorId) {
        record Restored(String id, String batchId, long version) {
        }
        // the same lock as publish and slot edits: the conflict check below reads PUBLISHED periods
        batches.lockTenantPublish(tenantId);
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenantId).addValue("holiday", holidayId);
        List<Cancelled> cancelled = jdbc.query("select e.id, e.school_id, " + VN_DAY + " d from teaching_schedule_events e"
                + " where e.tenant_id = :tenant and e.cancelled_by_holiday_id = :holiday order by e.id for update", p,
                (rs, i) -> new Cancelled(rs.getString("id"), rs.getString("school_id"), rs.getDate("d").toLocalDate()));
        List<String> ids = restorable(tenantId, holidayId, cancelled);
        if (ids.isEmpty()) return 0;
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Restored> restored = jdbc.query("update teaching_schedule_events set status = 'PUBLISHED',"
                        + " cancelled_by_holiday_id = null, cancel_reason = null, updated_by = :actor, updated_at = :now,"
                        + " record_version = record_version + 1 where tenant_id = :tenant and id in (:ids)"
                        + " and status = 'CANCELLED' returning id, batch_id, record_version",
                p.addValue("actor", actorId).addValue("now", Timestamp.from(now.toInstant())).addValue("ids", ids),
                (rs, i) -> new Restored(rs.getString("id"), rs.getString("batch_id"), rs.getLong("record_version")));
        // shortcut: one CalendarEventChanged per restored row; a batch event would change the contract of other modules
        for (Restored r : restored) {
            publisher.publishEvent(new CalendarEventChanged("schedule-event-changed:" + r.id() + ":" + r.version(),
                    tenantId, r.id(), r.batchId(), actorId, now, 1, r.version()));
        }
        return restored.size();
    }

    record Cancelled(String id, String schoolId, LocalDate day) {
    }

    /** The periods whose day no other cancelling holiday covers ({@code others}: day to school ids, "" is every school). */
    static List<String> notCovered(List<Cancelled> cancelled, Map<LocalDate, Set<String>> others) {
        return cancelled.stream().filter(c -> {
            Set<String> covering = others.getOrDefault(c.day(), Set.of());
            return !covering.contains("") && !covering.contains(c.schoolId());
        }).map(Cancelled::id).toList();
    }

    /**
     * Periods with an ERROR conflict against something else. A clash between two periods that are restored together was
     * already there when both were published, so it does not block (the old test data has one teacher in two schools).
     */
    static Set<String> blockedBy(List<ConflictDetailResponse> conflicts, Set<String> restoring) {
        Set<String> blocked = new HashSet<>();
        for (ConflictDetailResponse conflict : conflicts) {
            if (!"ERROR".equals(conflict.severity())) continue;
            if (conflict.other() != null && restoring.contains(conflict.other().eventId())) continue;
            blocked.add(conflict.mine().eventId());
        }
        return blocked;
    }

    /**
     * The cancelled periods that may go back: not on a day another cancelling holiday still covers, and without an
     * ERROR conflict against what was published while the day was off. The rest stay CANCELLED (logged).
     */
    private List<String> restorable(String tenantId, String holidayId, List<Cancelled> cancelled) {
        if (cancelled.isEmpty()) return List.of();
        Map<LocalDate, Set<String>> others = new HashMap<>();
        jdbc.query("select holiday_date, school_id from tk_holidays where tenant_id = :tenant and id <> :holiday"
                        + " and teaching_action = 'CANCEL' and holiday_date in (:days)",
                new MapSqlParameterSource("tenant", tenantId).addValue("holiday", holidayId)
                        .addValue("days", cancelled.stream().map(c -> java.sql.Date.valueOf(c.day())).distinct().toList()),
                rs -> {
                    others.computeIfAbsent(rs.getDate("holiday_date").toLocalDate(), k -> new HashSet<>())
                            .add(rs.getString("school_id") == null ? "" : rs.getString("school_id"));
                });
        List<String> free = notCovered(cancelled, others);
        if (free.isEmpty()) return free;
        Set<String> blocked = blockedBy(engine.detect(tenantId, loader.loadEvents(tenantId, free), new HashSet<>(free), false),
                new HashSet<>(free));
        if (blocked.size() + cancelled.size() - free.size() > 0) {
            LOG.warn("holiday {} deleted: {} periods stay cancelled (still a holiday: {}, conflict: {})", holidayId,
                    blocked.size() + cancelled.size() - free.size(), cancelled.size() - free.size(), blocked.size());
        }
        return free.stream().filter(id -> !blocked.contains(id)).toList();
    }

    @Override
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Map<String, Affected> affected(String tenantId, Collection<String> holidayIds) {
        Map<String, Affected> result = new HashMap<>();
        if (holidayIds.isEmpty()) return result;
        jdbc.query("select h.id, coalesce(sum(e.period_count), 0) periods, count(distinct e.school_id) schools"
                        + " from tk_holidays h join teaching_schedule_events e on e.tenant_id = h.tenant_id"
                        + " and e.start_at >= (h.holiday_date::timestamp at time zone 'Asia/Ho_Chi_Minh')"
                        + " and e.start_at < ((h.holiday_date + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh')"
                        + " and (h.school_id is null or e.school_id = h.school_id)"
                        + " and (e.status = 'PUBLISHED' or e.cancelled_by_holiday_id = h.id)"
                        + " where h.tenant_id = :tenant and h.id in (:ids) group by h.id",
                new MapSqlParameterSource("tenant", tenantId).addValue("ids", holidayIds),
                rs -> {
                    result.put(rs.getString("id"), new Affected(rs.getInt("periods"), rs.getInt("schools")));
                });
        return result;
    }

    @Override
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<DayLoad> dayLoads(String tenantId, LocalDate from, LocalDate to, Collection<String> schoolIds) {
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenantId)
                .addValue("from", java.sql.Date.valueOf(from)).addValue("to", java.sql.Date.valueOf(to.plusDays(1)));
        String scope = "";
        if (schoolIds != null) {
            if (schoolIds.isEmpty()) return List.of();
            scope = " and e.school_id in (:schools)";
            p.addValue("schools", schoolIds);
        }
        // the start_at range lets the tenant index work; the exact day is the Vietnam date of each row
        return jdbc.query("select " + VN_DAY + " d, sum(e.period_count) periods, count(distinct e.school_id) schools"
                        + " from teaching_schedule_events e where e.tenant_id = :tenant and e.status = 'PUBLISHED'"
                        + " and e.start_at >= (cast(:from as timestamp) at time zone 'Asia/Ho_Chi_Minh')"
                        + " and e.start_at < (cast(:to as timestamp) at time zone 'Asia/Ho_Chi_Minh')"
                        + scope + " group by d order by d", p,
                (rs, i) -> new DayLoad(rs.getDate("d").toLocalDate().toString(), rs.getInt("periods"), rs.getInt("schools")));
    }

    private static MapSqlParameterSource dayParams(String tenantId, String schoolId, Collection<LocalDate> dates) {
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenantId).addValue("days",
                dates.stream().map(java.sql.Date::valueOf).toList());
        if (schoolId != null) p.addValue("school", schoolId);
        // the start_at range lets the tenant index work (the exact day is still the Vietnam date of each row)
        LocalDate first = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate last = dates.stream().max(LocalDate::compareTo).orElseThrow();
        return p.addValue("rangeFrom", Timestamp.from(first.atStartOfDay(VIETNAM).toInstant()))
                .addValue("rangeTo", Timestamp.from(last.plusDays(1).atStartOfDay(VIETNAM).toInstant()));
    }

    private static String dayFilter(String schoolId) {
        return "e.start_at >= :rangeFrom and e.start_at < :rangeTo and " + VN_DAY + " in (:days)"
                + (schoolId == null ? "" : " and e.school_id = :school");
    }
}
