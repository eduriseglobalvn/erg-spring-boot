package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.SchoolPeriodTimesInput;
import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput;
import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput.Slot;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPeriodAssignmentResponse;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes period templates ("Giờ tiết của trường") and their dated assignment to schools. A template a batch already
 * uses is frozen: the grid of that batch must keep its times, so a new season is saved as a new template.
 */
@Service
public class PeriodTemplateAdminService {

    static final int MAX_ROWS = 12;
    static final int MAX_TIMED = 10;
    static final int MAX_NAME = 80;
    private static final Pattern HH_MM = Pattern.compile("\\d{2}:\\d{2}");
    private static final List<String> SESSIONS = List.of("MORNING", "AFTERNOON", "EVENING");
    /** The date the undated assignment older code wrote carries; shown as "no date". */
    static final LocalDate UNDATED = LocalDate.of(1970, 1, 1);

    private final JdbcTemplate jdbc;
    private final PeriodTemplateService templates;
    private final ObjectProvider<Clock> clock;

    public PeriodTemplateAdminService(JdbcTemplate jdbc, PeriodTemplateService templates, ObjectProvider<Clock> clock) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.clock = clock;
    }

    /** Schools that use the template (any date), so the caller can check it may touch all of them. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<String> assignedSchools(String tenantId, String templateId) {
        return jdbc.queryForList("select distinct school_id from school_period_templates where tenant_id = ? and template_id = ?",
                String.class, tenantId, templateId);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public boolean isDefault(String tenantId, String templateId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select coalesce(bool_or(is_default), false) from teaching_period_templates where tenant_id = ? and id = ?",
                Boolean.class, tenantId, templateId));
    }

    /** Create ({@code id} null) or replace name and slots of a template that no batch uses yet. */
    @Transactional("postgresTransactionManager")
    public PeriodTemplate save(String tenantId, TeachingPeriodTemplateInput in) {
        String name = validName(in.name());
        List<Slot> slots = validSlots(in.slots());
        String id = in.id();
        if (id == null) {
            id = IdGenerator.newId();
            jdbc.update("insert into teaching_period_templates (id, tenant_id, name, periods_per_day, is_default)"
                    + " values (?, ?, ?, ?, false)", id, tenantId, name, slots.size());
        } else {
            // row lock: two admins replacing the same template, or a batch being created, serialise here
            if (jdbc.queryForList("select id from teaching_period_templates where id = ? and tenant_id = ? for update",
                    String.class, id, tenantId).isEmpty()) {
                throw TeachingScheduleException.notFound("Khung tiết không tồn tại: " + id);
            }
            Integer used = jdbc.queryForObject("select count(*) from teaching_schedule_batches"
                    + " where tenant_id = ? and period_template_id = ? and status <> 'CANCELLED'", Integer.class, tenantId, id);
            if (used != null && used > 0) {
                throw TeachingScheduleException.conflict("TEMPLATE_IN_USE", "Bảng giờ này đang dùng cho " + used
                        + " đợt lịch. Lưu thành bảng giờ mới và áp dụng từ ngày muốn đổi.");
            }
            jdbc.update("update teaching_period_templates set name = ?, periods_per_day = ? where id = ?", name, slots.size(), id);
            jdbc.update("delete from teaching_period_template_slots where template_id = ?", id);
        }
        for (Slot slot : slots) {
            jdbc.update("insert into teaching_period_template_slots (template_id, period_no, session, start_time, end_time)"
                    + " values (?, ?, ?, ?::time, ?::time)", id, slot.periodNo(), slot.session(), slot.startTime(), slot.endTime());
        }
        return templates.byId(tenantId, id);
    }

    /** Assign a template to a school from a date; the same date again replaces the earlier choice. */
    @Transactional("postgresTransactionManager")
    public SchoolPeriodAssignmentResponse assign(String tenantId, String schoolId, String templateId, String effectiveFrom,
            String actorId) {
        LocalDate from = date(effectiveFrom);
        String schoolName = schoolName(tenantId, schoolId);
        PeriodTemplate template = templates.byId(tenantId, templateId);
        jdbc.update("insert into school_period_templates (school_id, tenant_id, template_id, effective_from, updated_by, updated_at)"
                + " values (?, ?, ?, ?, ?, now())"
                + " on conflict (school_id, effective_from) do update set template_id = excluded.template_id,"
                + " updated_by = excluded.updated_by, updated_at = now() where school_period_templates.tenant_id = excluded.tenant_id",
                schoolId, tenantId, templateId, from, actorId);
        return new SchoolPeriodAssignmentResponse(schoolId, schoolName, templateId, template.name(), from.toString(),
                from.equals(currentDate(tenantId, schoolId)));
    }

    /** One call for the board's "Lưu giờ tiết": a new template and its assignment commit together. */
    @Transactional("postgresTransactionManager")
    public SchoolPeriodAssignmentResponse saveForSchool(String tenantId, SchoolPeriodTimesInput in, String actorId) {
        schoolName(tenantId, in.schoolId());
        date(in.effectiveFrom());
        PeriodTemplate created = save(tenantId, new TeachingPeriodTemplateInput(null, in.name(), in.slots()));
        return assign(tenantId, in.schoolId(), created.id(), in.effectiveFrom(), actorId);
    }

    /** Dated assignments of the schools ({@code schoolIds} null = every school of the tenant), newest first per school. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<SchoolPeriodAssignmentResponse> assignments(String tenantId, Collection<String> schoolIds) {
        if (schoolIds != null && schoolIds.isEmpty()) return List.of();
        LocalDate today = today();
        List<Row> rows = new ArrayList<>(jdbc.query("select s.school_id, u.name school_name, s.template_id, t.name template_name,"
                + " s.effective_from from school_period_templates s join teaching_period_templates t on t.id = s.template_id"
                + " join education_units u on u.id = s.school_id where s.tenant_id = ? and t.tenant_id = ? and u.tenant_id = ?",
                (rs, i) -> new Row(rs.getString("school_id"), rs.getString("school_name"), rs.getString("template_id"),
                        rs.getString("template_name"), rs.getObject("effective_from", LocalDate.class)),
                tenantId, tenantId, tenantId));
        if (schoolIds != null) rows.removeIf(r -> !schoolIds.contains(r.schoolId()));
        Map<String, LocalDate> current = new HashMap<>();
        for (Row r : rows) {
            if (!r.from().isAfter(today)) current.merge(r.schoolId(), r.from(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return rows.stream().sorted(Comparator.comparing(Row::schoolName).thenComparing(Row::schoolId)
                        .thenComparing(Row::from, Comparator.reverseOrder()))
                .map(r -> new SchoolPeriodAssignmentResponse(r.schoolId(), r.schoolName(), r.templateId(), r.templateName(),
                        r.from().equals(UNDATED) ? null : r.from().toString(), r.from().equals(current.get(r.schoolId()))))
                .toList();
    }

    private record Row(String schoolId, String schoolName, String templateId, String templateName, LocalDate from) {}

    private LocalDate today() {
        return LocalDate.now(clock.getIfAvailable(Clock::systemUTC).withZone(ScheduleDataLoader.VN));
    }

    private LocalDate currentDate(String tenantId, String schoolId) {
        return jdbc.queryForObject("select max(effective_from) from school_period_templates where tenant_id = ? and school_id = ?"
                + " and effective_from <= ?", LocalDate.class, tenantId, schoolId, today());
    }

    private String schoolName(String tenantId, String schoolId) {
        List<String> found = jdbc.queryForList("select name from education_units where id = ? and tenant_id = ?", String.class,
                schoolId, tenantId);
        if (found.isEmpty()) throw TeachingScheduleException.notFound("Trường không tồn tại: " + schoolId);
        return found.get(0);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw TeachingScheduleException.badRequest("TEMPLATE_NAME_EMPTY", "Tên bảng giờ không được để trống.");
        if (name.length() > MAX_NAME) {
            throw TeachingScheduleException.badRequest("TEMPLATE_NAME_TOO_LONG", "Tên bảng giờ tối đa " + MAX_NAME + " ký tự.");
        }
        return name;
    }

    /** Periods 1..n in order, sessions never going back, timed periods in order, not overlapping, at most 10 of them. */
    static List<Slot> validSlots(List<Slot> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_ROWS) {
            throw TeachingScheduleException.badRequest("PERIODS_COUNT_INVALID", "Một bảng giờ có từ 1 đến " + MAX_ROWS + " tiết.");
        }
        List<Slot> slots = raw.stream().sorted(Comparator.comparingInt(Slot::periodNo)).toList();
        int timed = 0;
        int lastSession = 0;
        LocalTime lastEnd = null;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.periodNo() != i + 1) {
                throw TeachingScheduleException.badRequest("PERIOD_NUMBERS_INVALID", "Số tiết phải liên tục từ 1.");
            }
            int session = SESSIONS.indexOf(slot.session());
            if (session < 0) throw TeachingScheduleException.badRequest("SESSION_INVALID", "Buổi không hợp lệ: " + slot.session());
            if (session < lastSession) {
                throw TeachingScheduleException.badRequest("SESSION_ORDER_INVALID",
                        "Tiết buổi sáng phải đứng trước buổi chiều và buổi tối.");
            }
            lastSession = session;
            if (slot.startTime() == null && slot.endTime() == null) continue;
            if (slot.startTime() == null || slot.endTime() == null) {
                throw TeachingScheduleException.badRequest("PERIOD_TIME_INCOMPLETE",
                        "Tiết " + slot.periodNo() + " cần cả giờ bắt đầu và kết thúc.");
            }
            LocalTime start = time(slot.startTime(), slot.periodNo());
            LocalTime end = time(slot.endTime(), slot.periodNo());
            if (!start.isBefore(end)) {
                throw TeachingScheduleException.badRequest("PERIOD_TIME_ORDER",
                        "Tiết " + slot.periodNo() + ": giờ kết thúc phải sau giờ bắt đầu.");
            }
            if (lastEnd != null && start.isBefore(lastEnd)) {
                throw TeachingScheduleException.badRequest("PERIOD_TIME_OVERLAP",
                        "Tiết " + slot.periodNo() + " bắt đầu trước khi tiết trước kết thúc.");
            }
            lastEnd = end;
            timed++;
        }
        if (timed == 0) throw TeachingScheduleException.badRequest("NO_TIMED_PERIOD", "Cần ít nhất một tiết có giờ.");
        if (timed > MAX_TIMED) {
            throw TeachingScheduleException.badRequest("TOO_MANY_TIMED_PERIODS", "Tối đa " + MAX_TIMED + " tiết có giờ mỗi ngày.");
        }
        return slots;
    }

    private static LocalTime time(String value, int periodNo) {
        try {
            if (!HH_MM.matcher(value).matches()) throw new DateTimeParseException("", value, 0);
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw TeachingScheduleException.badRequest("PERIOD_TIME_INVALID", "Tiết " + periodNo + ": giờ không hợp lệ (HH:mm).");
        }
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw TeachingScheduleException.badRequest("INVALID_DATE", "Ngày không hợp lệ: " + value);
        }
    }
}
