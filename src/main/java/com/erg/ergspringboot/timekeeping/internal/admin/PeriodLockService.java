package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.PeriodStage;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PeriodLockView;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLock;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLockRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks / reopens a school's month. Locking needs the month to be over and no PENDING request touching it (409
 * "Còn n đơn chưa duyệt trong kỳ"); locking twice is a no-op. Reopening needs a reason and is audited; once
 * reopened, punches, approvals and recomputes reach the month again (they all check the open lock row).
 */
@Service
public class PeriodLockService {

    private final TkPeriodLockRepository locks;
    private final TkRequestRepository requests;
    private final AdminSupport support;

    PeriodLockService(TkPeriodLockRepository locks, TkRequestRepository requests, AdminSupport support) {
        this.locks = locks;
        this.requests = requests;
        this.support = support;
    }

    @Transactional
    public PeriodLockView lock(String tenantId, String actorId, String schoolId, String period) {
        YearMonth month = AdminSupport.period(period);
        support.requireSchool(tenantId, schoolId);
        TkPeriodLock row = locks.findByTenantIdAndSchoolIdAndPeriod(tenantId, schoolId, period).orElse(null);
        if (row != null && row.getReopenedAt() == null) return view(row);
        if (!support.today().isAfter(month.atEndOfMonth())) {
            throw AdminSupport.conflict("PERIOD_NOT_ENDED", "Kỳ công chưa kết thúc");
        }
        long pending = requests.countPendingTouching(tenantId, schoolId, month.atDay(1), month.atEndOfMonth());
        if (pending > 0) {
            throw AdminSupport.conflict("PENDING_REQUESTS", "Còn " + pending + " đơn chưa duyệt trong kỳ");
        }
        if (row == null) {
            row = new TkPeriodLock();
            row.setTenantId(tenantId);
            row.setSchoolId(schoolId);
            row.setPeriod(period);
        }
        Instant now = support.clock().instant();
        row.setLockedAt(now);
        row.setLockedBy(actorId);
        row.setReopenedAt(null);
        row.setReopenedBy(null);
        row = locks.save(row);
        support.audit(tenantId, actorId, "LOCK_PERIOD", "PERIOD", row.getId(),
                Map.of("schoolId", schoolId, "period", period));
        return view(row);
    }

    @Transactional
    public PeriodLockView reopen(String tenantId, String actorId, String schoolId, String period, String reason) {
        AdminSupport.period(period);
        if (reason == null || reason.isBlank()) throw AdminSupport.bad("REASON_REQUIRED", "Nhập lý do mở lại kỳ");
        if (reason.length() > 1000) throw AdminSupport.bad("REASON_TOO_LONG", "Lý do tối đa 1000 ký tự");
        support.requireSchool(tenantId, schoolId);
        TkPeriodLock row = locks.findByTenantIdAndSchoolIdAndPeriod(tenantId, schoolId, period)
                .filter(lock -> lock.getReopenedAt() == null)
                .orElseThrow(() -> AdminSupport.conflict("PERIOD_NOT_LOCKED", "Kỳ công chưa chốt"));
        row.setReopenedAt(support.clock().instant());
        row.setReopenedBy(actorId);
        support.audit(tenantId, actorId, "REOPEN_PERIOD", "PERIOD", row.getId(),
                Map.of("schoolId", schoolId, "period", period, "reason", reason.strip()));
        return view(row);
    }

    private static PeriodLockView view(TkPeriodLock row) {
        return new PeriodLockView(row.getSchoolId(), row.getPeriod(),
                row.getReopenedAt() == null ? PeriodStage.LOCKED : PeriodStage.REVIEW, row.getLockedAt(),
                row.getLockedBy(), row.getReopenedAt(), row.getReopenedBy());
    }
}
