package com.erg.ergspringboot.timekeeping.internal.day;

import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.internal.rules.DayInput;
import com.erg.ergspringboot.timekeeping.internal.rules.DayResult;
import com.erg.ergspringboot.timekeeping.internal.rules.WorkUnitCalculator;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a day's punches into its {@code tk_days} row. This first version only knows punches; leave, holidays and
 * explanations are added by the request flow (T5). Rejected punches are never stored, so every stored punch counts
 * (FLAGGED / PENDING_REVIEW are shown as flags, not dropped).
 */
@Service
public class DayAggregationService {

    private final TkPunchRepository punches;
    private final TkDayRepository days;
    private final TkShiftRepository shifts;
    private final WorkContextResolver context;
    private final Clock clock;

    DayAggregationService(TkPunchRepository punches, TkDayRepository days, TkShiftRepository shifts,
            WorkContextResolver context, Clock clock) {
        this.punches = punches;
        this.days = days;
        this.shifts = shifts;
        this.context = context;
        this.clock = clock;
    }

    /** First counted IN and latest counted OUT of the day. */
    public static Optional<TkPunch> firstIn(List<TkPunch> dayPunches) {
        return dayPunches.stream().filter(p -> counts(p) && p.getDirection() == PunchDirection.IN).findFirst();
    }

    public static Optional<TkPunch> latestOut(List<TkPunch> dayPunches) {
        return dayPunches.stream().filter(p -> counts(p) && p.getDirection() == PunchDirection.OUT)
                .reduce((first, second) -> second);
    }

    private static boolean counts(TkPunch p) {
        return p.getStatus() != PunchStatus.REJECTED;
    }

    @Transactional
    public void recompute(String tenantId, String userId, LocalDate date) {
        List<TkPunch> dayPunches = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        Optional<TkPunch> in = firstIn(dayPunches);
        Optional<TkPunch> out = latestOut(dayPunches);
        TkPunch any = in.or(() -> out).orElse(null);
        if (any == null || any.getShiftId() == null) return;
        TkShift shift = shifts.findById(any.getShiftId()).orElse(null);
        if (shift == null) return;
        TkPolicy policy = context.policyFor(tenantId, any.getSchoolId(), date);
        Instant now = clock.instant();
        boolean closed = date.isBefore(LocalDate.ofInstant(now, WorkContextResolver.ZONE))
                || now.isAfter(date.atTime(shift.getEndTime()).plusMinutes(60).atZone(WorkContextResolver.ZONE).toInstant());
        DayResult result = WorkUnitCalculator.compute(new DayInput(shift.getStartTime(), shift.getEndTime(),
                shift.getWorkUnits(), in.map(TkPunch::getServerTime).orElse(null),
                out.map(TkPunch::getServerTime).orElse(null), policy.getLateToleranceMin(), policy.getLateBlockMin(),
                WorkContextResolver.ZONE, closed));

        TkDay day = days.findByTenantIdAndUserIdAndWorkDate(tenantId, userId, date).orElseGet(TkDay::new);
        day.setTenantId(tenantId);
        day.setUserId(userId);
        day.setWorkDate(date);
        day.setSchoolId(any.getSchoolId());
        day.setShiftId(shift.getId());
        day.setStatus(result.status());
        day.setWorkUnits(result.workUnits().setScale(1, RoundingMode.HALF_UP));
        day.setLateMinutes(result.lateMinutes());
        day.setEarlyMinutes(result.earlyMinutes());
        day.setDeductMinutes(result.deductMinutes());
        day.setMissing(result.missing());
        day.setRemote(in.map(TkPunch::getRemote).orElse(false) || out.map(TkPunch::getRemote).orElse(false));
        day.setInPunchId(in.map(TkPunch::getId).orElse(null));
        day.setOutPunchId(out.map(TkPunch::getId).orElse(null));
        days.save(day);
    }
}
