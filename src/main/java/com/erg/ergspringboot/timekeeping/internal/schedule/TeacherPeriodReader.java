package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Teaching periods for a punch or a day row, read so that a calendar failure can never cost the caller its
 * transaction. The calendar read use case is {@code @Transactional}: called inside the punch transaction it would
 * join it, and an exception would mark the punch rollback-only even when caught. The read therefore runs with the
 * caller's transaction suspended (NOT_SUPPORTED; the calendar opens its own read-only transaction on a second pooled
 * connection for the duration of the read) and a failure is logged and reported as empty.
 */
@Component
public class TeacherPeriodReader {

    private static final Logger log = LoggerFactory.getLogger(TeacherPeriodReader.class);

    private final CalendarReadPort calendar;
    private final TransactionTemplate suspended;

    public TeacherPeriodReader(CalendarReadPort calendar, PlatformTransactionManager transactionManager) {
        this.calendar = calendar;
        this.suspended = new TransactionTemplate(transactionManager);
        this.suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /** Empty when the calendar read failed (logged with ids and exception class only). */
    public Optional<List<TeacherPeriod>> read(String tenantId, String userId, LocalDate from, LocalDate to) {
        try {
            return Optional.of(suspended.execute(status -> calendar.teacherPeriods(tenantId, userId, from, to)));
        } catch (RuntimeException ex) {
            log.warn("Teacher periods read failed for user {} from {} to {}: {}", userId, from, to,
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
