package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Period templates (khung tiết): which clock time a period number has, per school with a tenant default. */
@Service
public class PeriodTemplateService {

    /** A slot without times is a "trống giờ" period: shown in the grid but not schedulable. */
    public record PeriodSlot(int periodNo, String session, LocalTime start, LocalTime end) {
        public boolean hasTime() {
            return start != null && end != null;
        }
    }

    public record PeriodTemplate(String id, String name, int periodsPerDay, List<PeriodSlot> slots) {
        public PeriodSlot slot(int periodNo) {
            return slots.stream().filter(s -> s.periodNo() == periodNo).findFirst()
                    .orElseThrow(() -> TeachingScheduleException.badRequest(
                            "PERIOD_OUT_OF_RANGE", "Tiết " + periodNo + " không có trong khung tiết " + name));
        }
    }

    static final String DEFAULT_NAME = "THCS · 10 tiết/ngày";

    private final JdbcTemplate jdbc;

    public PeriodTemplateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional("postgresTransactionManager")
    public List<PeriodTemplate> list(String tenantId) {
        ensureDefault(tenantId);
        return jdbc.queryForList("select id from teaching_period_templates where tenant_id = ? order by is_default desc, name",
                String.class, tenantId).stream().map(this::load).toList();
    }

    /** The template a school uses: its own assignment, else the tenant default (created on first use). */
    @Transactional("postgresTransactionManager")
    public PeriodTemplate templateForSchool(String tenantId, String schoolId) {
        List<String> assigned = jdbc.queryForList(
                "select t.id from school_period_templates s join teaching_period_templates t on t.id = s.template_id"
                        + " where s.school_id = ? and t.tenant_id = ?", String.class, schoolId, tenantId);
        return load(assigned.isEmpty() ? ensureDefault(tenantId) : assigned.get(0));
    }

    @Transactional("postgresTransactionManager")
    public PeriodTemplate byId(String tenantId, String templateId) {
        List<String> found = jdbc.queryForList(
                "select id from teaching_period_templates where id = ? and tenant_id = ?", String.class, templateId, tenantId);
        if (found.isEmpty()) throw TeachingScheduleException.notFound("Khung tiết không tồn tại: " + templateId);
        return load(templateId);
    }

    /** The slot of a period; {@code PERIOD_HAS_NO_TIME} when the school has not configured its clock time. */
    public PeriodSlot slot(PeriodTemplate template, int periodNo) {
        PeriodSlot slot = template.slot(periodNo);
        if (!slot.hasTime()) {
            throw TeachingScheduleException.badRequest("PERIOD_HAS_NO_TIME", "Tiết " + periodNo + " chưa có giờ.");
        }
        return slot;
    }

    private String ensureDefault(String tenantId) {
        try {
            return jdbc.queryForObject(
                    "select id from teaching_period_templates where tenant_id = ? and is_default", String.class, tenantId);
        } catch (EmptyResultDataAccessException missing) {
            String id = IdGenerator.newId();
            // The partial unique index makes a racing creator a no-op; re-read whichever row won.
            jdbc.update("insert into teaching_period_templates (id, tenant_id, name, periods_per_day, is_default)"
                    + " values (?, ?, ?, 10, true) on conflict do nothing", id, tenantId, DEFAULT_NAME);
            String winner = jdbc.queryForObject(
                    "select id from teaching_period_templates where tenant_id = ? and is_default", String.class, tenantId);
            if (id.equals(winner)) seedSlots(id);
            return winner;
        }
    }

    private void seedSlots(String templateId) {
        Object[][] rows = {
                {1, "MORNING", "07:15", "08:00"}, {2, "MORNING", "08:00", "08:45"},
                {3, "MORNING", "09:15", "10:00"}, {4, "MORNING", "10:00", "10:45"}, {5, "MORNING", null, null},
                {6, "AFTERNOON", "13:30", "14:15"}, {7, "AFTERNOON", "14:15", "15:00"},
                {8, "AFTERNOON", "15:30", "16:15"}, {9, "AFTERNOON", "16:15", "17:00"}, {10, "AFTERNOON", null, null}};
        for (Object[] row : rows) {
            jdbc.update("insert into teaching_period_template_slots (template_id, period_no, session, start_time, end_time)"
                    + " values (?, ?, ?, ?::time, ?::time)", templateId, row[0], row[1], row[2], row[3]);
        }
    }

    private PeriodTemplate load(String templateId) {
        Map<String, Object> head = jdbc.queryForMap(
                "select name, periods_per_day from teaching_period_templates where id = ?", templateId);
        List<PeriodSlot> slots = new ArrayList<>(jdbc.query(
                "select period_no, session, start_time, end_time from teaching_period_template_slots"
                        + " where template_id = ? order by period_no",
                (rs, i) -> new PeriodSlot(rs.getInt("period_no"), rs.getString("session"),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class)),
                templateId));
        return new PeriodTemplate(templateId, (String) head.get("name"), ((Number) head.get("periods_per_day")).intValue(), slots);
    }
}
