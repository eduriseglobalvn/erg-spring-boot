package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.SaturdayMode;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SaturdayModeView;
import com.erg.ergspringboot.timekeeping.persistence.TkSaturdayMode;
import com.erg.ergspringboot.timekeeping.persistence.TkSaturdayModeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The per-day Saturday choice for the whole system (KE_HOACH_LICH_DAY.md 2.6 point 6): no row means OFFICE. */
@Service
public class SaturdayModeService {

    private final TkSaturdayModeRepository modes;
    private final DayOffSupport dayOff;
    private final AdminSupport support;

    SaturdayModeService(TkSaturdayModeRepository modes, DayOffSupport dayOff, AdminSupport support) {
        this.modes = modes;
        this.dayOff = dayOff;
        this.support = support;
    }

    /** Every Saturday of the month with its mode. */
    @Transactional(readOnly = true)
    public List<SaturdayModeView> saturdayModes(String tenantId, String month) {
        YearMonth ym = AdminSupport.period(month);
        Map<LocalDate, SaturdayMode> chosen = modes.findByTenantIdAndWorkDateBetween(tenantId, ym.atDay(1), ym.atEndOfMonth())
                .stream().collect(Collectors.toMap(TkSaturdayMode::getWorkDate, TkSaturdayMode::getMode));
        List<SaturdayModeView> result = new ArrayList<>();
        for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY) result.add(new SaturdayModeView(d, chosen.getOrDefault(d, SaturdayMode.OFFICE)));
        }
        return result;
    }

    /** Choosing OFFICE removes the row. A day in a locked period is refused (PERIOD_LOCKED). */
    @Transactional
    public SaturdayModeView setSaturdayMode(String tenantId, String actorId, LocalDate date, SaturdayMode mode) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw AdminSupport.bad("SATURDAY_MODE_NOT_SATURDAY", "Chỉ chọn chế độ cho ngày thứ 7");
        }
        if (mode == null) throw AdminSupport.bad("MODE_REQUIRED", "Chọn chế độ thứ 7");
        dayOff.requireOpen(tenantId, null, date);
        TkSaturdayMode row = modes.findByTenantIdAndWorkDate(tenantId, date).orElse(null);
        SaturdayMode before = row == null ? SaturdayMode.OFFICE : row.getMode();
        if (mode == SaturdayMode.OFFICE) {
            if (row != null) {
                modes.delete(row);
                modes.flush();
            }
        } else {
            if (row == null) {
                row = new TkSaturdayMode();
                row.setTenantId(tenantId);
                row.setWorkDate(date);
                row.setCreatedBy(actorId);
            }
            row.setMode(mode);
            modes.saveAndFlush(row);
        }
        support.auditChange(tenantId, actorId, "SET_SATURDAY_MODE", "SATURDAY_MODE", date.toString(),
                Map.of("date", date.toString(), "mode", before.name()), Map.of("date", date.toString(), "mode", mode.name()));
        dayOff.recomputeStoredDays(tenantId, null, date);
        return new SaturdayModeView(date, mode);
    }
}
