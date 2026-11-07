package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ShiftCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ShiftConfig;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteConfig;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRuleRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A04 punch sites and shifts. Rows are read with the caller's tenant, school ids are checked against it, every
 * write is audited (before/after), and a site or shift already used by punches/days/rules is deactivated instead
 * of deleted. One active shift per school and weekday; no overnight shifts (02:00–23:00).
 */
@Service
public class SiteShiftConfigService {

    private static final LocalTime EARLIEST_START = LocalTime.of(2, 0);
    private static final LocalTime LATEST_END = LocalTime.of(23, 0);
    private static final Set<BigDecimal> UNITS = Set.of(new BigDecimal("0.5"), new BigDecimal("1.0"));

    private final TkSiteRepository sites;
    private final TkShiftRepository shifts;
    private final TkRemoteRuleRepository remoteRules;
    private final TkPunchRepository punches;
    private final TkDayRepository days;
    private final AdminSupport support;

    SiteShiftConfigService(TkSiteRepository sites, TkShiftRepository shifts, TkRemoteRuleRepository remoteRules,
            TkPunchRepository punches, TkDayRepository days, AdminSupport support) {
        this.sites = sites;
        this.shifts = shifts;
        this.remoteRules = remoteRules;
        this.punches = punches;
        this.days = days;
        this.support = support;
    }

    // ---- sites

    @Transactional(readOnly = true)
    public List<SiteConfig> sites(String tenantId, String schoolId) {
        List<TkSite> rows = schoolId == null ? sites.findByTenantIdOrderByNameAsc(tenantId)
                : sites.findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, support.requireSchool(tenantId, schoolId).getId());
        return rows.stream().map(SiteShiftConfigService::site).toList();
    }

    @Transactional
    public SiteConfig saveSite(String tenantId, String actorId, String id, SiteCommand c) {
        if (c == null) throw AdminSupport.bad("BODY_REQUIRED", "Thiếu dữ liệu");
        TkSite row = id == null ? new TkSite() : sites.findByIdAndTenantId(id, tenantId).orElseThrow(siteNotFound());
        Map<String, Object> before = id == null ? null : AdminSupport.summary(site(row));
        support.requireSchool(tenantId, c.schoolId());
        String name = AdminSupport.text(c.name(), 255, "NAME_REQUIRED", "Nhập tên vị trí");
        if (c.lat() == null || c.lng() == null || c.lat().abs().compareTo(BigDecimal.valueOf(90)) > 0
                || c.lng().abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw AdminSupport.bad("INVALID_LOCATION", "Toạ độ không hợp lệ");
        }
        int radius = c.radiusM() == null ? 150 : c.radiusM();
        if (radius < 50 || radius > 500) throw AdminSupport.bad("INVALID_RADIUS", "Bán kính phải từ 50 đến 500 m");
        if (c.address() != null && c.address().length() > 500) {
            throw AdminSupport.bad("ADDRESS_TOO_LONG", "Địa chỉ tối đa 500 ký tự");
        }
        row.setTenantId(tenantId);
        row.setSchoolId(c.schoolId());
        row.setName(name);
        row.setAddress(c.address() == null || c.address().isBlank() ? null : c.address().strip());
        row.setLat(c.lat());
        row.setLng(c.lng());
        row.setRadiusM(radius);
        row.setActive(c.active() == null || c.active());
        SiteConfig saved = site(sites.save(row));
        support.auditChange(tenantId, actorId, id == null ? "CREATE_SITE" : "UPDATE_SITE", "SITE", saved.id(), before,
                AdminSupport.summary(saved));
        return saved;
    }

    @Transactional
    public void deleteSite(String tenantId, String actorId, String id) {
        TkSite row = sites.findByIdAndTenantId(id, tenantId).orElseThrow(siteNotFound());
        boolean used = punches.existsByTenantIdAndSiteId(tenantId, id);
        if (used) {
            row.setActive(false);
        } else {
            sites.delete(row);
        }
        support.auditChange(tenantId, actorId, used ? "DEACTIVATE_SITE" : "DELETE_SITE", "SITE", id,
                AdminSupport.summary(site(row)), null);
    }

    // ---- shifts

    @Transactional(readOnly = true)
    public List<ShiftConfig> shifts(String tenantId, String schoolId) {
        List<TkShift> rows = schoolId == null ? shifts.findByTenantId(tenantId)
                : shifts.findByTenantIdAndSchoolIdOrderByStartTimeAsc(tenantId,
                        support.requireSchool(tenantId, schoolId).getId());
        return rows.stream().map(SiteShiftConfigService::shift).toList();
    }

    @Transactional
    public ShiftConfig saveShift(String tenantId, String actorId, String id, ShiftCommand c) {
        if (c == null) throw AdminSupport.bad("BODY_REQUIRED", "Thiếu dữ liệu");
        TkShift existing = id == null ? null : shifts.findByIdAndTenantId(id, tenantId).orElseThrow(shiftNotFound());
        Map<String, Object> before = existing == null ? null : AdminSupport.summary(shift(existing));
        support.requireSchool(tenantId, c.schoolId());
        String name = AdminSupport.text(c.name(), 255, "NAME_REQUIRED", "Nhập tên ca");
        if (c.startTime() == null || c.endTime() == null || c.startTime().isBefore(EARLIEST_START)
                || c.endTime().isAfter(LATEST_END) || !c.endTime().isAfter(c.startTime())) {
            throw AdminSupport.bad("INVALID_SHIFT_TIME", "Giờ ca phải trong 02:00–23:00 và giờ ra sau giờ vào");
        }
        BigDecimal units = c.workUnits() == null ? BigDecimal.ONE : c.workUnits();
        if (UNITS.stream().noneMatch(u -> u.compareTo(units) == 0)) {
            throw AdminSupport.bad("INVALID_WORK_UNITS", "Số công của ca là 0,5 hoặc 1");
        }
        if (c.weekdays() == null || c.weekdays().isEmpty()
                || c.weekdays().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw AdminSupport.bad("INVALID_WEEKDAYS", "Chọn ngày trong tuần (1–7)");
        }
        Integer[] weekdays = c.weekdays().stream().distinct().sorted().toArray(Integer[]::new);
        boolean active = c.active() == null || c.active();
        TkShift row = existing == null ? new TkShift() : existing;
        boolean revives = existing != null && active && existing.getDeactivatedAt() != null;
        if (revives || (existing != null && rewritesHistory(existing, c, units, weekdays)
                && days.existsByTenantIdAndShiftId(tenantId, existing.getId()))) {
            // days built on this row keep it, and a retired row never comes back (it would cover its retired days):
            // the edit becomes a new row in effect from today
            retire(existing, true);
            shifts.save(existing);
            row = new TkShift();
        }
        String rowId = row.getId();
        if (active) {
            List<Integer> mine = Arrays.asList(weekdays);
            boolean overlaps = shifts.findByTenantIdAndSchoolIdAndActiveTrueOrderByStartTimeAsc(tenantId, c.schoolId())
                    .stream().filter(s -> !s.getId().equals(rowId))
                    .anyMatch(s -> Arrays.stream(s.getWeekdays()).anyMatch(mine::contains));
            if (overlaps) throw AdminSupport.conflict("SHIFT_OVERLAP", "Mỗi ngày trong tuần chỉ có một ca");
        }
        row.setTenantId(tenantId);
        row.setSchoolId(c.schoolId());
        row.setName(name);
        row.setStartTime(c.startTime());
        row.setEndTime(c.endTime());
        row.setWorkUnits(units.setScale(1));
        row.setWeekdays(weekdays);
        // a new shift is in effect from today (server clock), never retroactively
        if (row != existing) row.setCreatedAt(support.clock().instant().atOffset(ZoneOffset.UTC));
        retire(row, !active);
        ShiftConfig saved = shift(shifts.save(row));
        String action = existing == null ? "CREATE_SHIFT" : row == existing ? "UPDATE_SHIFT" : "REPLACE_SHIFT";
        support.auditChange(tenantId, actorId, action, "SHIFT", saved.id(), before, AdminSupport.summary(saved));
        return saved;
    }

    @Transactional
    public void deleteShift(String tenantId, String actorId, String id) {
        TkShift row = shifts.findByIdAndTenantId(id, tenantId).orElseThrow(shiftNotFound());
        boolean used = punches.existsByTenantIdAndShiftId(tenantId, id) || days.existsByTenantIdAndShiftId(tenantId, id)
                || remoteRules.existsByTenantIdAndShiftId(tenantId, id);
        if (used) {
            retire(row, true);
        } else {
            shifts.delete(row);
        }
        support.auditChange(tenantId, actorId, used ? "DEACTIVATE_SHIFT" : "DELETE_SHIFT", "SHIFT", id,
                AdminSupport.summary(shift(row)), null);
    }

    /** Anything but the name changes how the row's past days are rebuilt. */
    private static boolean rewritesHistory(TkShift row, ShiftCommand c, BigDecimal units, Integer[] weekdays) {
        return !c.schoolId().equals(row.getSchoolId()) || !c.startTime().equals(row.getStartTime())
                || !c.endTime().equals(row.getEndTime()) || units.compareTo(row.getWorkUnits()) != 0
                || !Arrays.equals(weekdays, row.getWeekdays());
    }

    /** A retired shift keeps rebuilding the days before its deactivation day and applies to none after it. */
    private void retire(TkShift row, boolean retired) {
        if (retired && row.getDeactivatedAt() == null) row.setDeactivatedAt(support.clock().instant());
        if (!retired) row.setDeactivatedAt(null);
        row.setActive(!retired);
    }

    private static SiteConfig site(TkSite s) {
        return new SiteConfig(s.getId(), s.getSchoolId(), s.getName(), s.getAddress(), s.getLat(), s.getLng(),
                s.getRadiusM(), Boolean.TRUE.equals(s.getActive()));
    }

    private static ShiftConfig shift(TkShift s) {
        return new ShiftConfig(s.getId(), s.getSchoolId(), s.getName(), s.getStartTime(), s.getEndTime(),
                s.getWorkUnits(), s.getWeekdays() == null ? List.of() : List.of(s.getWeekdays()),
                Boolean.TRUE.equals(s.getActive()));
    }

    private static Supplier<RuntimeException> siteNotFound() {
        return () -> AdminSupport.notFound("Không tìm thấy vị trí chấm công");
    }

    private static Supplier<RuntimeException> shiftNotFound() {
        return () -> AdminSupport.notFound("Không tìm thấy ca");
    }
}
