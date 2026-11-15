package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarHolidayPort;
import com.erg.ergspringboot.calendar.LessonReportCounter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
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

    private static final String VN_DAY = "(e.start_at at time zone 'Asia/Ho_Chi_Minh')::date";

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final ObjectProvider<LessonReportCounter> reports;
    private final Clock clock;

    public ScheduleHolidayService(NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher publisher,
            ObjectProvider<LessonReportCounter> reports, Clock clock) {
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
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
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
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public int restorePeriods(String tenantId, String holidayId, String actorId) {
        record Restored(String id, String batchId, long version) {
        }
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", tenantId).addValue("holiday", holidayId);
        List<String> ids = jdbc.queryForList("select id from teaching_schedule_events where tenant_id = :tenant"
                + " and cancelled_by_holiday_id = :holiday order by id for update", p, String.class);
        if (ids.isEmpty()) return 0;
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Restored> restored = jdbc.query("update teaching_schedule_events set status = 'PUBLISHED',"
                        + " cancelled_by_holiday_id = null, cancel_reason = null, updated_by = :actor, updated_at = :now,"
                        + " record_version = record_version + 1 where tenant_id = :tenant and id in (:ids)"
                        + " and status = 'CANCELLED' returning id, batch_id, record_version",
                p.addValue("actor", actorId).addValue("now", Timestamp.from(now.toInstant())).addValue("ids", ids),
                (rs, i) -> new Restored(rs.getString("id"), rs.getString("batch_id"), rs.getLong("record_version")));
        for (Restored r : restored) {
            publisher.publishEvent(new CalendarEventChanged("schedule-event-changed:" + r.id() + ":" + r.version(),
                    tenantId, r.id(), r.batchId(), actorId, now, 1, r.version()));
        }
        return restored.size();
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
        return p;
    }

    private static String dayFilter(String schoolId) {
        return VN_DAY + " in (:days)" + (schoolId == null ? "" : " and e.school_id = :school");
    }
}
