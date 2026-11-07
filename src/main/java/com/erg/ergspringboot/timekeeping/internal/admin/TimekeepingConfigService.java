package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.RemoteRuleKind;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PolicyVersions;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemoteRuleCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemoteRuleConfig;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLockRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicyRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRule;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRuleRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A04 configuration: policies, holidays and remote-work rules (sites and shifts: SiteShiftConfigService). Every row is read with the caller's
 * tenant and every school id is checked against it; every write leaves a {@code tk_audit_log} row with a
 * before/after summary. A site or shift already used by punches/days is deactivated instead of deleted.
 */
@Service
public class TimekeepingConfigService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TkShiftRepository shifts;
    private final TkPolicyRepository policies;
    private final TkHolidayRepository holidays;
    private final TkRemoteRuleRepository remoteRules;
    private final UserRepository users;
    private final WorkContextResolver context;
    private final AdminSupport support;
    private final TkPeriodLockRepository locks;
    private final TkDayRepository dayRows;
    private final DayAggregationService aggregation;

    TimekeepingConfigService(TkShiftRepository shifts, TkPolicyRepository policies, TkHolidayRepository holidays,
            TkRemoteRuleRepository remoteRules, UserRepository users, WorkContextResolver context, AdminSupport support,
            TkPeriodLockRepository locks, TkDayRepository dayRows, DayAggregationService aggregation) {
        this.locks = locks;
        this.dayRows = dayRows;
        this.aggregation = aggregation;
        this.shifts = shifts;
        this.policies = policies;
        this.holidays = holidays;
        this.remoteRules = remoteRules;
        this.users = users;
        this.context = context;
        this.support = support;
    }

    // ---- policies

    @Transactional(readOnly = true)
    public PolicyVersions policy(String tenantId, String schoolId) {
        support.requireSchool(tenantId, schoolId);
        LocalDate today = support.today();
        return new PolicyVersions(policy(context.policyFor(tenantId, schoolId, today)),
                policies.findFirstByTenantIdAndSchoolIdAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
                        tenantId, schoolId, today).map(TimekeepingConfigService::policy).orElse(null));
    }

    /**
     * Writes the version that takes effect on the first day of the next month ("thay đổi có hiệu lực từ kỳ công tiếp
     * theo"), replacing an earlier scheduled version for that day.
     */
    @Transactional
    public PolicyConfig savePolicy(String tenantId, String actorId, String schoolId, PolicyCommand c) {
        if (c == null) throw AdminSupport.bad("BODY_REQUIRED", "Thiếu dữ liệu");
        support.requireSchool(tenantId, schoolId);
        LocalDate effectiveFrom = YearMonth.from(support.today()).plusMonths(1).atDay(1);
        TkPolicy row = policies.findByTenantIdAndSchoolIdAndEffectiveFrom(tenantId, schoolId, effectiveFrom)
                .orElseGet(TkPolicy::new);
        Map<String, Object> before = row.getId() == null ? null : AdminSupport.summary(policy(row));
        // a null field keeps the already scheduled value, else the one in force today (each field reads before its set)
        TkPolicy now = row.getId() != null ? row : context.policyFor(tenantId, schoolId, support.today());
        row.setTenantId(tenantId);
        row.setSchoolId(schoolId);
        row.setEffectiveFrom(effectiveFrom);
        row.setLateToleranceMin(range(c.lateToleranceMin(), now.getLateToleranceMin(), 0, 60, "Dung sai đi muộn"));
        row.setLateBlockMin(range(c.lateBlockMin(), now.getLateBlockMin(), 1, 120, "Block trừ muộn"));
        row.setRequireFace(c.requireFace() == null ? now.getRequireFace() : c.requireFace());
        row.setAllowOutside(c.allowOutside() == null ? now.getAllowOutside() : c.allowOutside());
        row.setExplanationsPerMonth(range(c.explanationsPerMonth(), now.getExplanationsPerMonth(), 0, 31,
                "Số lần giải trình mỗi tháng"));
        row.setExplanationWindowDays(range(c.explanationWindowDays(), now.getExplanationWindowDays(), 1, 31,
                "Hạn giải trình"));
        row.setAnnualLeaveDays(range(c.annualLeaveDays(), now.getAnnualLeaveDays(), 0, 60, "Số ngày phép năm"));
        row.setSeniorityStepYears(range(c.seniorityStepYears(), now.getSeniorityStepYears(), 1, 20,
                "Số năm thâm niên mỗi bậc"));
        row.setLockDayOfMonth(range(c.lockDayOfMonth(), now.getLockDayOfMonth(), 1, 28, "Ngày chốt công"));
        row.setApprovalChains(c.approvalChains() == null ? now.getApprovalChains() : chains(c.approvalChains()));
        PolicyConfig saved = policy(policies.save(row));
        support.auditChange(tenantId, actorId, "UPDATE_POLICY", "POLICY", saved.id(), before,
                AdminSupport.summary(saved));
        return saved;
    }

    // ---- holidays

    @Transactional(readOnly = true)
    public List<HolidayConfig> holidays(String tenantId, String schoolId, Integer year) {
        if (schoolId != null) support.requireSchool(tenantId, schoolId);
        int y = year == null ? support.today().getYear() : year;
        if (y < 2000 || y > 2100) throw AdminSupport.bad("INVALID_YEAR", "Năm không hợp lệ");
        return holidays.findByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(tenantId, LocalDate.of(y, 1, 1),
                        LocalDate.of(y, 12, 31)).stream()
                .filter(h -> schoolId == null || h.getSchoolId() == null || schoolId.equals(h.getSchoolId()))
                .map(TimekeepingConfigService::holiday).toList();
    }

    @Transactional
    public HolidayConfig createHoliday(String tenantId, String actorId, HolidayCommand c) {
        if (c == null || c.date() == null) throw AdminSupport.bad("DATE_REQUIRED", "Chọn ngày lễ");
        String name = AdminSupport.text(c.name(), 255, "NAME_REQUIRED", "Nhập tên ngày lễ");
        String schoolId = c.schoolId() == null || c.schoolId().isBlank() ? null : c.schoolId();
        if (schoolId != null) support.requireSchool(tenantId, schoolId);
        requireOpen(tenantId, schoolId, c.date());
        if (holidays.existsByTenantIdAndSchoolIdAndHolidayDate(tenantId, schoolId, c.date())) {
            throw AdminSupport.conflict("HOLIDAY_EXISTS", "Ngày lễ này đã có");
        }
        TkHoliday row = new TkHoliday();
        row.setTenantId(tenantId);
        row.setSchoolId(schoolId);
        row.setHolidayDate(c.date());
        row.setName(name);
        HolidayConfig saved = holiday(holidays.saveAndFlush(row));
        support.auditChange(tenantId, actorId, "CREATE_HOLIDAY", "HOLIDAY", saved.id(), null,
                AdminSupport.summary(saved));
        recomputeStoredDays(tenantId, schoolId, c.date());
        return saved;
    }

    @Transactional
    public void deleteHoliday(String tenantId, String actorId, String id) {
        TkHoliday row = holidays.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy ngày lễ"));
        requireOpen(tenantId, row.getSchoolId(), row.getHolidayDate());
        holidays.delete(row);
        holidays.flush();
        recomputeStoredDays(tenantId, row.getSchoolId(), row.getHolidayDate());
        support.auditChange(tenantId, actorId, "DELETE_HOLIDAY", "HOLIDAY", id, AdminSupport.summary(holiday(row)),
                null);
    }

    /** 409 PERIOD_LOCKED when the month is locked for the school, or (tenant-wide holiday) for any school. */
    private void requireOpen(String tenantId, String schoolId, LocalDate date) {
        if (schoolId != null) {
            context.requireOpen(tenantId, schoolId, date);
        } else if (locks.existsByTenantIdAndPeriodAndReopenedAtIsNull(tenantId, YearMonth.from(date).toString())) {
            throw WorkContextResolver.periodLocked();
        }
    }

    /** A holiday on a past or current day rebuilds the stored day rows it touches (the school, or every school). */
    private void recomputeStoredDays(String tenantId, String schoolId, LocalDate date) {
        if (date.isAfter(support.today())) return;
        dayRows.findByTenantIdAndWorkDate(tenantId, date).stream()
                .filter(d -> schoolId == null || schoolId.equals(d.getSchoolId()))
                .map(TkDay::getUserId).distinct().toList()
                .forEach(userId -> aggregation.recompute(tenantId, userId, date));
    }

    // ---- remote-work rules

    @Transactional(readOnly = true)
    public List<RemoteRuleConfig> remoteRules(String tenantId, String schoolId) {
        List<TkRemoteRule> rows = schoolId == null ? remoteRules.findByTenantId(tenantId)
                : remoteRules.findByTenantIdAndSchoolId(tenantId, support.requireSchool(tenantId, schoolId).getId());
        return rows.stream().map(TimekeepingConfigService::rule).toList();
    }

    @Transactional
    public RemoteRuleConfig saveRemoteRule(String tenantId, String actorId, String id, RemoteRuleCommand c) {
        if (c == null || c.kind() == null) throw AdminSupport.bad("KIND_REQUIRED", "Chọn loại quy tắc");
        TkRemoteRule row = id == null ? new TkRemoteRule()
                : remoteRules.findByIdAndTenantId(id, tenantId).orElseThrow(ruleNotFound());
        Map<String, Object> before = id == null ? null : AdminSupport.summary(rule(row));
        support.requireSchool(tenantId, c.schoolId());
        List<String> userIds = List.of();
        Integer weekday = null;
        if (c.kind() == RemoteRuleKind.USERS) {
            userIds = c.userIds() == null ? List.of() : c.userIds().stream().distinct().toList();
            if (userIds.isEmpty() || userIds.size() > 500) {
                throw AdminSupport.bad("INVALID_USERS", "Chọn từ 1 đến 500 nhân sự");
            }
            if (users.findByTenantIdAndIdInAnyStatus(tenantId, userIds).size() != userIds.size()) {
                throw AdminSupport.bad("UNKNOWN_USERS", "Có nhân sự không thuộc đơn vị");
            }
        } else if (c.kind() == RemoteRuleKind.WEEKDAY) {
            if (c.weekday() == null || c.weekday() < 1 || c.weekday() > 7) {
                throw AdminSupport.bad("INVALID_WEEKDAY", "Chọn ngày trong tuần (1–7)");
            }
            weekday = c.weekday();
        }
        String shiftId = c.shiftId() == null || c.shiftId().isBlank() ? null : c.shiftId();
        if (shiftId != null && shifts.findByIdAndTenantId(shiftId, tenantId)
                .filter(s -> s.getSchoolId().equals(c.schoolId())).isEmpty()) {
            throw AdminSupport.bad("UNKNOWN_SHIFT", "Ca không thuộc cơ sở này");
        }
        row.setTenantId(tenantId);
        row.setSchoolId(c.schoolId());
        row.setKind(c.kind());
        row.setUserIds(new java.util.ArrayList<>(userIds));
        row.setWeekday(weekday);
        row.setShiftId(shiftId);
        row.setActive(c.active() == null || c.active());
        RemoteRuleConfig saved = rule(remoteRules.save(row));
        support.auditChange(tenantId, actorId, id == null ? "CREATE_REMOTE_RULE" : "UPDATE_REMOTE_RULE", "REMOTE_RULE",
                saved.id(),
                before, AdminSupport.summary(saved));
        return saved;
    }

    @Transactional
    public void deleteRemoteRule(String tenantId, String actorId, String id) {
        TkRemoteRule row = remoteRules.findByIdAndTenantId(id, tenantId).orElseThrow(ruleNotFound());
        remoteRules.delete(row);
        support.auditChange(tenantId, actorId, "DELETE_REMOTE_RULE", "REMOTE_RULE", id,
                AdminSupport.summary(rule(row)), null);
    }

    // ---- helpers

    /** Shape of {@code approval_chains} (T5b): {TYPE:[{minDays:n>=0, steps:["MANAGER"|"SCHOOL_ROLE:X",...]}]}. */
    static String chains(Map<String, Object> chains) {
        for (Map.Entry<String, Object> type : chains.entrySet()) {
            if (Arrays.stream(RequestType.values()).noneMatch(t -> t.name().equals(type.getKey()))
                    || !(type.getValue() instanceof List<?> rules)) {
                throw invalidChains();
            }
            for (Object entry : rules) {
                if (!(entry instanceof Map<?, ?> rule) || !(rule.get("steps") instanceof List<?> steps) || steps.isEmpty()
                        || rule.keySet().stream().anyMatch(k -> !"minDays".equals(k) && !"steps".equals(k))) {
                    throw invalidChains();
                }
                Object min = rule.get("minDays");
                if (min != null && (!(min instanceof Number n) || n.doubleValue() < 0)) throw invalidChains();
                if (steps.stream().anyMatch(s -> !(s instanceof String step)
                        || !("MANAGER".equals(step) || step.matches("SCHOOL_ROLE:[A-Z_]{1,50}")))) {
                    throw invalidChains();
                }
            }
        }
        try {
            return JSON.writeValueAsString(chains);
        } catch (JacksonException unwritable) {
            throw invalidChains();
        }
    }

    private static com.erg.ergspringboot.timekeeping.TimekeepingException invalidChains() {
        return AdminSupport.bad("INVALID_APPROVAL_CHAINS", "Cấu hình luồng duyệt không hợp lệ");
    }

    private static int range(Integer value, int current, int min, int max, String label) {
        int v = value == null ? current : value;
        if (v < min || v > max) {
            throw AdminSupport.bad("OUT_OF_RANGE", label + " phải từ " + min + " đến " + max);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static PolicyConfig policy(TkPolicy p) {
        Map<String, Object> chains;
        try {
            chains = p.getApprovalChains() == null ? Map.of() : JSON.readValue(p.getApprovalChains(), LinkedHashMap.class);
        } catch (JacksonException unreadable) {
            chains = Map.of();
        }
        return new PolicyConfig(p.getId(), p.getSchoolId(), p.getEffectiveFrom(), p.getLateToleranceMin(),
                p.getLateBlockMin(), p.getRequireFace(), p.getAllowOutside(), p.getExplanationsPerMonth(),
                p.getExplanationWindowDays(), p.getAnnualLeaveDays(), p.getSeniorityStepYears(), p.getLockDayOfMonth(),
                chains);
    }

    private static HolidayConfig holiday(TkHoliday h) {
        return new HolidayConfig(h.getId(), h.getSchoolId(), h.getHolidayDate(), h.getName());
    }

    private static RemoteRuleConfig rule(TkRemoteRule r) {
        return new RemoteRuleConfig(r.getId(), r.getSchoolId(), r.getKind(), r.getUserIds(), r.getWeekday(),
                r.getShiftId(), Boolean.TRUE.equals(r.getActive()));
    }

    private static Supplier<RuntimeException> ruleNotFound() {
        return () -> AdminSupport.notFound("Không tìm thấy quy tắc");
    }
}
