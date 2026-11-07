package com.erg.ergspringboot.timekeeping.internal.rules;

import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.Missing;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Turns a day's first IN / last OUT into a status, work units and minutes.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Times are read in {@link DayInput#zone()}, never the JVM default zone.
 *   <li>Lateness is whole minutes after shift start. Within {@code toleranceMin} (inclusive) it counts as on time:
 *       {@code lateMinutes} is 0 and the status stays FULL. Beyond it, {@code lateMinutes} is the raw minutes after
 *       shift start (07:37 for a 07:30 shift is 7), not the excess over tolerance. Early leave works the same
 *       way against shift end.
 *   <li>Work units stay {@code shiftUnits} for LATE_EARLY. Lateness never reduces the unit; it only produces
 *       {@code deductMinutes}: for late and for early leave separately, 0 if within tolerance, otherwise the minutes
 *       rounded up to a multiple of {@code blockMin}; the two are summed.
 *   <li>Closed day: IN only is MISSING_PUNCH, half the shift units, missing OUT; OUT only is MISSING_PUNCH, half the
 *       units, missing IN; no punch at all is MISSING_PUNCH, 0 units, missing IN (the roll-up step turns that into
 *       ABSENT_UNPAID when there is no leave request).
 *   <li>Open day with IN only: judged on lateness alone and provisionally credited the full units, no missing punch.
 *       An open day with no punch yet is reported like the closed one (MISSING_PUNCH, 0, IN); callers should not
 *       compute a day that has not started.
 * </ul>
 */
public final class WorkUnitCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private WorkUnitCalculator() {}

    public static DayResult compute(DayInput d) {
        if (d.in() == null && d.out() == null) {
            return new DayResult(DayStatus.MISSING_PUNCH, BigDecimal.ZERO, 0, 0, 0, Missing.IN);
        }
        if (d.in() == null) {
            return new DayResult(DayStatus.MISSING_PUNCH, half(d), 0, 0, 0, Missing.IN);
        }
        if (d.out() == null && d.closed()) {
            return new DayResult(DayStatus.MISSING_PUNCH, half(d), 0, 0, 0, Missing.OUT);
        }

        int late = beyondTolerance(minutesBetween(d.shiftStart(), local(d.in(), d)), d);
        int early = d.out() == null ? 0 : beyondTolerance(minutesBetween(local(d.out(), d), d.shiftEnd()), d);
        int deduct = blocks(late, d) + blocks(early, d);
        DayStatus status = late > 0 || early > 0 ? DayStatus.LATE_EARLY : DayStatus.FULL;
        return new DayResult(status, d.shiftUnits(), late, early, deduct, null);
    }

    private static BigDecimal half(DayInput d) {
        return d.shiftUnits().divide(TWO);
    }

    private static LocalTime local(java.time.Instant t, DayInput d) {
        return t.atZone(d.zone()).toLocalTime();
    }

    private static int minutesBetween(LocalTime from, LocalTime to) {
        return (int) ChronoUnit.MINUTES.between(from, to);
    }

    private static int beyondTolerance(int minutes, DayInput d) {
        return minutes > d.toleranceMin() ? minutes : 0;
    }

    private static int blocks(int minutes, DayInput d) {
        int block = Math.max(1, d.blockMin());
        return (minutes + block - 1) / block * block;
    }
}
