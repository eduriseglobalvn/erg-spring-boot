package com.erg.ergspringboot.timekeeping.internal.rules;

import com.erg.ergspringboot.timekeeping.DayPart;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Counts leave days that actually fall on working days. */
public final class LeaveDayCounter {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private LeaveDayCounter() {}

    /**
     * One per workday in {@code from..to} inclusive that is not a holiday. AM/PM is half a day and only valid for a
     * single day (otherwise IllegalArgumentException); on a non-workday or holiday it counts 0.
     */
    public static BigDecimal count(
            LocalDate from, LocalDate to, DayPart part, Set<DayOfWeek> workdays, Set<LocalDate> holidays) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        boolean half = part != DayPart.FULL;
        if (half && !from.equals(to)) {
            throw new IllegalArgumentException("A half day needs from == to");
        }
        long days = from.datesUntil(to.plusDays(1))
                .filter(day -> workdays.contains(day.getDayOfWeek()) && !holidays.contains(day))
                .count();
        return half ? HALF.multiply(BigDecimal.valueOf(days)) : BigDecimal.valueOf(days);
    }
}
