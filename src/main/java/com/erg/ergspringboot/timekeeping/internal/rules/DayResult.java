package com.erg.ergspringboot.timekeeping.internal.rules;

import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.Missing;
import java.math.BigDecimal;

/** {@code deductMinutes} is the payroll deduction ("Phút trừ"); {@code missing} is null unless status is MISSING_PUNCH. */
public record DayResult(
        DayStatus status, BigDecimal workUnits, int lateMinutes, int earlyMinutes, int deductMinutes, Missing missing) {}
