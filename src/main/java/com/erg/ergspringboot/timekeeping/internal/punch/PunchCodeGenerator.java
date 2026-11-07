package com.erg.ergspringboot.timekeeping.internal.punch;

import com.erg.ergspringboot.timekeeping.persistence.TkCodeCounterRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Punch codes {@code CC-ddMMyy-nnnn}: nnnn counts per tenant per work day, from an atomic counter row. */
@Component
public class PunchCodeGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("ddMMyy");

    private final TkCodeCounterRepository counters;

    PunchCodeGenerator(TkCodeCounterRepository counters) {
        this.counters = counters;
    }

    @Transactional
    public String next(String tenantId, LocalDate workDate) {
        String day = workDate.format(DAY);
        long value = counters.increment(IdGenerator.newId(), tenantId, "CC", day);
        return "CC-%s-%04d".formatted(day, value);
    }
}
