package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.SchoolShortName;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minutes a teacher needs between two schools. A pair is stored once, ordered so that school_a_id < school_b_id. */
@Service
public class TravelTimeService {

    public record School(String id, String name, String shortName) {}

    public record TravelTime(School schoolA, School schoolB, int minutes, OffsetDateTime updatedAt) {}

    /** The tenant's travel table read once, for callers that ask about many pairs in one request. */
    public record TravelMatrix(Map<String, Integer> byPair, int defaultMinutes) {
        public int requiredMinutes(String schoolX, String schoolY) {
            if (schoolX.equals(schoolY)) return 0;
            String a = lower(schoolX, schoolY);
            return byPair.getOrDefault(a + '|' + (a.equals(schoolX) ? schoolY : schoolX), defaultMinutes);
        }
    }

    private final JdbcTemplate jdbc;
    private final int defaultMinutes;

    public TravelTimeService(JdbcTemplate jdbc,
            @Value("${erg.calendar.travel.default-minutes:50}") int defaultMinutes) {
        this.jdbc = jdbc;
        this.defaultMinutes = defaultMinutes;
    }

    /** Required minutes between two schools in either order; the configured default when the pair has no row. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public int requiredMinutes(String tenantId, String schoolX, String schoolY) {
        if (schoolX.equals(schoolY)) return 0;
        String a = lower(schoolX, schoolY);
        String b = a.equals(schoolX) ? schoolY : schoolX;
        try {
            return jdbc.queryForObject("select minutes from school_travel_times"
                    + " where tenant_id = ? and school_a_id = ? and school_b_id = ?", Integer.class, tenantId, a, b);
        } catch (EmptyResultDataAccessException none) {
            return defaultMinutes;
        }
    }

    /** One select for the whole (small) per-tenant table; same answers as {@link #requiredMinutes}. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public TravelMatrix matrix(String tenantId) {
        Map<String, Integer> pairs = new HashMap<>();
        jdbc.query("select school_a_id, school_b_id, minutes from school_travel_times where tenant_id = ?",
                rs -> {
                    pairs.put(rs.getString("school_a_id") + '|' + rs.getString("school_b_id"), rs.getInt("minutes"));
                }, tenantId);
        return new TravelMatrix(pairs, defaultMinutes);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<TravelTime> list(String tenantId, String search, int page, int size) {
        String like = search == null || search.isBlank() ? "%" : "%" + search.trim().toLowerCase() + "%";
        return jdbc.query("select t.minutes, t.updated_at, a.id a_id, a.name a_name, b.id b_id, b.name b_name"
                + " from school_travel_times t"
                + " join education_units a on a.id = t.school_a_id join education_units b on b.id = t.school_b_id"
                + " where t.tenant_id = ? and (lower(a.name) like ? or lower(b.name) like ?)"
                + " order by a.name, b.name limit ? offset ?",
                (rs, i) -> new TravelTime(school(rs.getString("a_id"), rs.getString("a_name")),
                        school(rs.getString("b_id"), rs.getString("b_name")), rs.getInt("minutes"),
                        rs.getObject("updated_at", OffsetDateTime.class)),
                tenantId, like, like, Math.max(1, Math.min(size, 200)), Math.max(0, page) * Math.max(1, Math.min(size, 200)));
    }

    @Transactional("postgresTransactionManager")
    public TravelTime upsert(String tenantId, String schoolX, String schoolY, int minutes, String actorId) {
        if (minutes < 0 || minutes > 240) {
            throw TeachingScheduleException.badRequest("INVALID_TRAVEL_MINUTES", "Thời gian di chuyển phải từ 0 đến 240 phút.");
        }
        if (schoolX.equals(schoolY)) {
            throw TeachingScheduleException.badRequest("INVALID_TRAVEL_PAIR", "Hai trường phải khác nhau.");
        }
        School x = requireSchool(tenantId, schoolX);
        School y = requireSchool(tenantId, schoolY);
        String a = lower(schoolX, schoolY);
        String b = a.equals(schoolX) ? schoolY : schoolX;
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("insert into school_travel_times (tenant_id, school_a_id, school_b_id, minutes, updated_by, updated_at)"
                + " values (?, ?, ?, ?, ?, ?)"
                + " on conflict (tenant_id, school_a_id, school_b_id)"
                + " do update set minutes = excluded.minutes, updated_by = excluded.updated_by, updated_at = excluded.updated_at",
                tenantId, a, b, minutes, actorId, now);
        return new TravelTime(a.equals(x.id()) ? x : y, a.equals(x.id()) ? y : x, minutes, now);
    }

    @Transactional("postgresTransactionManager")
    public boolean delete(String tenantId, String schoolX, String schoolY) {
        String a = lower(schoolX, schoolY);
        String b = a.equals(schoolX) ? schoolY : schoolX;
        return jdbc.update("delete from school_travel_times where tenant_id = ? and school_a_id = ? and school_b_id = ?",
                tenantId, a, b) > 0;
    }

    private School requireSchool(String tenantId, String schoolId) {
        List<School> found = jdbc.query("select id, name from education_units where id = ? and tenant_id = ?",
                (rs, i) -> school(rs.getString("id"), rs.getString("name")), schoolId, tenantId);
        if (found.isEmpty()) throw TeachingScheduleException.notFound("Trường không tồn tại: " + schoolId);
        return found.get(0);
    }

    private static School school(String id, String name) {
        return new School(id, name, SchoolShortName.of(name));
    }

    private static String lower(String x, String y) {
        return x.compareTo(y) <= 0 ? x : y;
    }
}
