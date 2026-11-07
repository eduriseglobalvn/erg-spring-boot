package com.erg.ergspringboot.timekeeping.internal.rules;

import java.time.LocalTime;
import java.util.Optional;

/** Proposes the OUT time to prefill when someone explains a missing punch. */
public final class ExplanationSuggester {

    private ExplanationSuggester() {}

    /** The later of shift end and the end of the person's last class/period that day. */
    public static LocalTime suggestOut(LocalTime shiftEnd, Optional<LocalTime> lastPeriodEnd) {
        return lastPeriodEnd.filter(shiftEnd::isBefore).orElse(shiftEnd);
    }
}
