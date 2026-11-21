package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.calendar.CalendarHolidayPort;
import com.erg.ergspringboot.calendar.CalendarHolidayPort.Affected;
import com.erg.ergspringboot.calendar.CalendarHolidayPort.HolidayImpact;
import com.erg.ergspringboot.exception.HolidayAffectsPastPeriodsException;
import com.erg.ergspringboot.timekeeping.HolidayKind;
import com.erg.ergspringboot.timekeeping.HolidayPayMode;
import com.erg.ergspringboot.timekeeping.HolidayTeachingAction;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayConfig;
import com.erg.ergspringboot.timekeeping.internal.SchoolScope;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Days off (KE_HOACH_LICH_DAY.md 2.6, B11): kind, pay mode and what happens to teaching periods. A change to the
 * holiday and to the periods it cancels or restores commits together; a day in a locked period is refused first.
 * New days off are always system-wide ({@code school_id} null); an old row with a school keeps it.
 */
@Service
public class HolidayService {

    static final int MAX_RANGE_DAYS = 62;

    /** One day off to write; the single shape both the form and the calendar import produce. */
    record Draft(LocalDate date, String name, HolidayKind kind, HolidayPayMode payMode, HolidayTeachingAction action) {}

    private final TkHolidayRepository holidays;
    private final CalendarHolidayPort calendar;
    private final DayOffSupport dayOff;
    private final AdminSupport support;

    HolidayService(TkHolidayRepository holidays, CalendarHolidayPort calendar, DayOffSupport dayOff, AdminSupport support) {
        this.holidays = holidays;
        this.calendar = calendar;
        this.dayOff = dayOff;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public List<HolidayConfig> holidays(String tenantId, String schoolId, Integer year) {
        return holidays(tenantId, SchoolScope.TENANT, schoolId, year);
    }

    /** Tenant-level days off are visible to everyone; a school's own rows only inside the caller's scope. */
    @Transactional(readOnly = true)
    public List<HolidayConfig> holidays(String tenantId, SchoolScope scope, String schoolId, Integer year) {
        if (schoolId != null) support.requireSchool(tenantId, scope, schoolId);
        int y = year == null ? support.today().getYear() : year;
        if (y < 2000 || y > 2100) throw AdminSupport.bad("INVALID_YEAR", "Năm không hợp lệ");
        List<TkHoliday> rows = holidays.findByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(tenantId,
                LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)).stream()
                .filter(h -> h.getSchoolId() == null || (schoolId == null ? scope.allows(h.getSchoolId())
                        : schoolId.equals(h.getSchoolId()))).toList();
        Map<String, Affected> affected = calendar.affected(tenantId, rows.stream().map(TkHoliday::getId).toList());
        Map<String, String> creators = support.names(tenantId,
                rows.stream().map(TkHoliday::getCreatedBy).filter(id -> id != null).toList());
        return rows.stream().map(h -> view(h, creators.get(h.getCreatedBy()), affected.get(h.getId()))).toList();
    }

    /** One row per day of {@code from..to}; all or nothing, including the confirmation for periods already taught. */
    @Transactional
    public List<HolidayConfig> createHoliday(String tenantId, String actorId, HolidayCommand c) {
        if (c == null || c.from() == null) throw AdminSupport.bad("DATE_REQUIRED", "Chọn ngày nghỉ");
        LocalDate to = c.to() == null ? c.from() : c.to();
        if (to.isBefore(c.from()) || ChronoUnit.DAYS.between(c.from(), to) >= MAX_RANGE_DAYS) {
            throw AdminSupport.bad("INVALID_DATE_RANGE", "Khoảng ngày nghỉ tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        HolidayKind kind = required(c.kind());
        HolidayPayMode pay = payMode(kind, c.payMode());
        HolidayTeachingAction action = c.teachingAction() == null ? HolidayTeachingAction.CANCEL : c.teachingAction();
        String name = AdminSupport.text(c.name(), 255, "NAME_REQUIRED", "Nhập tên ngày nghỉ");
        List<Draft> drafts = new ArrayList<>();
        for (LocalDate d = c.from(); !d.isAfter(to); d = d.plusDays(1)) drafts.add(new Draft(d, name, kind, pay, action));
        return createAll(tenantId, actorId, drafts, Boolean.TRUE.equals(c.confirmPast()));
    }

    /** Writes the drafts (none may exist yet) and applies each to the calendar. Used by the form and the import. */
    List<HolidayConfig> createAll(String tenantId, String actorId, List<Draft> drafts, boolean confirmPast) {
        for (Draft d : drafts) {
            dayOff.requireOpen(tenantId, null, d.date());
            if (holidays.existsByTenantIdAndSchoolIdAndHolidayDate(tenantId, null, d.date())) {
                throw AdminSupport.conflict("HOLIDAY_EXISTS", "Ngày nghỉ này đã có");
            }
        }
        requireConfirmed(tenantId, null, drafts.stream().filter(d -> d.action() == HolidayTeachingAction.CANCEL)
                .map(Draft::date).toList(), confirmPast);
        List<HolidayConfig> saved = new ArrayList<>();
        for (Draft d : drafts) {
            TkHoliday row = new TkHoliday();
            row.setTenantId(tenantId);
            row.setHolidayDate(d.date());
            row.setName(d.name());
            row.setKind(d.kind());
            row.setPayMode(d.payMode());
            row.setTeachingAction(d.action());
            row.setCreatedBy(actorId);
            row = holidays.saveAndFlush(row);
            if (d.action() == HolidayTeachingAction.CANCEL) {
                calendar.cancelPeriods(tenantId, null, List.of(d.date()), row.getId(), d.name(), actorId);
            }
            HolidayConfig view = view(row, null, null);
            support.auditChange(tenantId, actorId, "CREATE_HOLIDAY", "HOLIDAY", view.id(), null, AdminSupport.summary(view));
            dayOff.recomputeStoredDays(tenantId, null, d.date());
            saved.add(view);
        }
        return saved;
    }

    /** Edits one day: the date, name, kind, pay mode or teaching action. Periods are re-applied only when the day or action changed. */
    @Transactional
    public HolidayConfig updateHoliday(String tenantId, String actorId, String id, HolidayCommand c) {
        if (c == null) throw AdminSupport.bad("BODY_REQUIRED", "Thiếu dữ liệu");
        TkHoliday row = holidays.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy ngày nghỉ"));
        HolidayConfig before = view(row, null, null);
        LocalDate oldDate = row.getHolidayDate();
        LocalDate newDate = c.from() == null ? oldDate : c.from();
        HolidayKind kind = required(c.kind());
        HolidayPayMode pay = payMode(kind, c.payMode());
        HolidayTeachingAction action = c.teachingAction() == null ? HolidayTeachingAction.CANCEL : c.teachingAction();
        String name = AdminSupport.text(c.name(), 255, "NAME_REQUIRED", "Nhập tên ngày nghỉ");
        dayOff.requireOpen(tenantId, row.getSchoolId(), oldDate);
        dayOff.requireOpen(tenantId, row.getSchoolId(), newDate);
        if (!newDate.equals(oldDate) && holidays.existsByTenantIdAndSchoolIdAndHolidayDate(tenantId, row.getSchoolId(), newDate)) {
            throw AdminSupport.conflict("HOLIDAY_EXISTS", "Ngày nghỉ này đã có");
        }
        boolean wasCancelling = row.getTeachingAction() == HolidayTeachingAction.CANCEL;
        boolean cancelling = action == HolidayTeachingAction.CANCEL;
        boolean reapply = cancelling && (!wasCancelling || !newDate.equals(oldDate));
        if (reapply) requireConfirmed(tenantId, row.getSchoolId(), List.of(newDate), Boolean.TRUE.equals(c.confirmPast()));
        if (wasCancelling && (!cancelling || !newDate.equals(oldDate))) {
            calendar.restorePeriods(tenantId, row.getId(), actorId);
        }
        row.setHolidayDate(newDate);
        row.setName(name);
        row.setKind(kind);
        row.setPayMode(pay);
        row.setTeachingAction(action);
        row = holidays.saveAndFlush(row);
        if (reapply) calendar.cancelPeriods(tenantId, row.getSchoolId(), List.of(newDate), row.getId(), name, actorId);
        HolidayConfig after = view(row, null, null);
        support.auditChange(tenantId, actorId, "UPDATE_HOLIDAY", "HOLIDAY", id, AdminSupport.summary(before),
                AdminSupport.summary(after));
        dayOff.recomputeStoredDays(tenantId, row.getSchoolId(), oldDate);
        if (!newDate.equals(oldDate)) dayOff.recomputeStoredDays(tenantId, row.getSchoolId(), newDate);
        return after;
    }

    @Transactional
    public void deleteHoliday(String tenantId, String actorId, String id) {
        TkHoliday row = holidays.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy ngày nghỉ"));
        dayOff.requireOpen(tenantId, row.getSchoolId(), row.getHolidayDate());
        HolidayConfig before = view(row, null, null);
        calendar.restorePeriods(tenantId, id, actorId);
        holidays.delete(row);
        holidays.flush();
        dayOff.recomputeStoredDays(tenantId, row.getSchoolId(), row.getHolidayDate());
        support.auditChange(tenantId, actorId, "DELETE_HOLIDAY", "HOLIDAY", id, AdminSupport.summary(before), null);
    }

    /** 409 HOLIDAY_AFFECTS_PAST_PERIODS (nothing written yet) when started periods would be cancelled without a confirmation. */
    private void requireConfirmed(String tenantId, String schoolId, List<LocalDate> dates, boolean confirmPast) {
        if (confirmPast || dates.isEmpty()) return;
        HolidayImpact impact = calendar.pastImpact(tenantId, schoolId, new TreeSet<>(dates));
        if (impact.any()) throw new HolidayAffectsPastPeriodsException(impact);
    }

    private static HolidayKind required(HolidayKind kind) {
        if (kind == null) throw AdminSupport.bad("HOLIDAY_KIND_REQUIRED", "Chọn loại ngày nghỉ");
        return kind;
    }

    /** Pay mode is required (no default); state holidays and compensatory days are always paid. */
    static HolidayPayMode payMode(HolidayKind kind, HolidayPayMode pay) {
        if (pay == null) throw AdminSupport.bad("HOLIDAY_PAY_MODE_REQUIRED", "Chọn cách tính lương");
        if (kind != HolidayKind.COMPANY && pay != HolidayPayMode.PAID) {
            throw AdminSupport.bad("HOLIDAY_STATE_MUST_BE_PAID", "Lễ nhà nước và nghỉ bù luôn có lương");
        }
        return pay;
    }

    /** Dates that already have a day off, for the import preview. */
    Set<LocalDate> existingDates(String tenantId, int year) {
        Set<LocalDate> dates = new TreeSet<>();
        holidays.findByTenantIdAndHolidayDateBetweenOrderByHolidayDateAsc(tenantId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)).forEach(h -> dates.add(h.getHolidayDate()));
        return dates;
    }

    private static HolidayConfig view(TkHoliday h, String createdByName, Affected affected) {
        return new HolidayConfig(h.getId(), h.getSchoolId(), h.getHolidayDate(), h.getName(), h.getKind(), h.getPayMode(),
                h.getTeachingAction(), createdByName, affected == null ? 0 : affected.periods(),
                affected == null ? 0 : affected.schools());
    }
}
