package com.erg.ergspringboot.timekeeping.internal.rules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * One person's day against one shift. {@code in}/{@code out} are the first and last valid punches (null if none);
 * {@code closed} is false while the day is still running, so a missing OUT is not yet a missing punch.
 */
public record DayInput(
        LocalTime shiftStart,
        LocalTime shiftEnd,
        BigDecimal shiftUnits,
        Instant in,
        Instant out,
        int toleranceMin,
        int blockMin,
        ZoneId zone,
        boolean closed) {}
