package com.erg.ergspringboot.timekeeping.internal.day;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * At 00:30 (Asia/Ho_Chi_Minh) closes yesterday for every tenant that has a shift: a missing OUT becomes a missing
 * punch and a day without punch or request becomes ABSENT_UNPAID. One instance runs it (cluster lock); each tenant
 * runs under its own tenant context, and a failing tenant is logged and skipped. Locked periods are left alone.
 */
@Component
public class DailyCloseJob {

    static final String JOB_NAME = "timekeeping-daily-close";
    private static final Logger log = LoggerFactory.getLogger(DailyCloseJob.class);

    private final TkShiftRepository shifts;
    private final DayAggregationService days;
    private final ClusterJobLock jobLock;
    private final Clock clock;

    DailyCloseJob(TkShiftRepository shifts, DayAggregationService days, ClusterJobLock jobLock, Clock clock) {
        this.shifts = shifts;
        this.days = days;
        this.jobLock = jobLock;
        this.clock = clock;
    }

    /** Returns the rows written, 0 when another instance holds the lock. */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public int run() {
        Integer rows = jobLock.callExclusively(JOB_NAME, this::closeYesterday);
        return rows == null ? 0 : rows;
    }

    private int closeYesterday() {
        LocalDate yesterday = LocalDate.ofInstant(clock.instant(), WorkContextResolver.ZONE).minusDays(1);
        List<String> tenants;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            tenants = shifts.findTenantIds();
        }
        int rows = 0;
        for (String tenantId : tenants) {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
                rows += days.closeDay(tenantId, yesterday);
            } catch (RuntimeException ex) {
                log.warn("Daily close failed for tenant {} date {}: {}", tenantId, yesterday,
                        ex.getClass().getSimpleName());
            }
        }
        return rows;
    }
}
