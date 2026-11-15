package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.timekeeping.SaturdayMode;
import com.erg.ergspringboot.timekeeping.persistence.TkSaturdayMode;
import com.erg.ergspringboot.timekeeping.persistence.TkSaturdayModeRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The per-day Saturday choice (KE_HOACH_LICH_DAY.md 2.6 point 6). No row = OFFICE, so only WFH, PAID_OFF and
 * UNPAID_OFF are ever returned. A Saturday row beats the weekly {@code tk_remote_rules} WEEKDAY rule (decided
 * 15/11/2026, Q6): {@link WorkContextResolver#isRemote} answers from the row before it looks at any rule.
 */
@Component
public class SaturdayModeResolver {

    private final TkSaturdayModeRepository modes;

    SaturdayModeResolver(TkSaturdayModeRepository modes) {
        this.modes = modes;
    }

    /** The mode of {@code date} when it is a Saturday with a non-OFFICE row. No query for any other weekday. */
    public Optional<SaturdayMode> modeOn(String tenantId, LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) return Optional.empty();
        return modes.findByTenantIdAndWorkDate(tenantId, date).map(TkSaturdayMode::getMode)
                .filter(mode -> mode != SaturdayMode.OFFICE);
    }

    /** Saturdays in the range that are a day off (paid or unpaid) for the audience of {@link #appliesTo}. */
    public Set<LocalDate> offDays(String tenantId, LocalDate from, LocalDate to) {
        Set<LocalDate> off = new HashSet<>();
        for (TkSaturdayMode row : modes.findByTenantIdAndWorkDateBetween(tenantId, from, to)) {
            if (row.getMode() == SaturdayMode.PAID_OFF || row.getMode() == SaturdayMode.UNPAID_OFF) off.add(row.getWorkDate());
        }
        return off;
    }

    /**
     * WHO a Saturday mode applies to: the single place to change when the owner answers question 6b.
     * Provisional default (15/11/2026): everyone whose shift that day includes Saturday, except timekeepers paid by
     * teaching period.
     * shortcut: "paid by teaching period" is not modelled in this branch (CBG owns it), so nobody is excluded yet;
     * return false for them here once CBG adds the flag.
     */
    public static boolean appliesTo(TkShift shift, String userId) {
        return shift != null && Arrays.asList(shift.getWeekdays()).contains(DayOfWeek.SATURDAY.getValue());
    }
}
