package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.TeachingPeriod;
import com.erg.ergspringboot.lms.LessonReportNotifier;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.ReportTeachingIssueCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingConfirmationView;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLog;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLogRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingConfirmation;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingConfirmationRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonth;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthLineRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher confirmation, issue report and HR close of a teaching-hours month (KE_HOACH_CONG_BAO_GIANG.md 2.2).
 * A confirmation records the two totals the teacher saw; when the timetable moves them the teacher goes back to PENDING
 * and is told ({@link #recheck}). Closing freezes both role sheets into {@code tk_teaching_month_lines}.
 */
@Service
public class TeachingMonthService {

    private static final Logger log = LoggerFactory.getLogger(TeachingMonthService.class);

    private final TeachingHoursService hours;
    private final TkTeachingMonthRepository months;
    private final TkTeachingConfirmationRepository confirmations;
    private final TkTeachingMonthLineRepository snapshots;
    private final TkAuditLogRepository audit;
    private final LessonReportNotifier notifier;
    private final Clock clock;
    private final TkRequestRepository requests;

    TeachingMonthService(TeachingHoursService hours, TkTeachingMonthRepository months,
            TkTeachingConfirmationRepository confirmations, TkTeachingMonthLineRepository snapshots,
            TkAuditLogRepository audit, LessonReportNotifier notifier, Clock clock, TkRequestRepository requests) {
        this.requests = requests;
        this.hours = hours;
        this.months = months;
        this.confirmations = confirmations;
        this.snapshots = snapshots;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public TeachingConfirmationView confirm(String tenantId, String userId, YearMonth month, ConfirmTeachingCommand command) {
        requireOpen(tenantId, month);
        if (!hours.ended(month)) throw conflict("MONTH_NOT_ENDED", "Tháng chưa kết thúc, chưa thể xác nhận");
        int[] mine = hours.liveTotals(tenantId, month, userId).getOrDefault(userId, new int[2]);
        if (mine[0] == 0 && mine[1] == 0) throw conflict("NO_TEACHING_PERIODS", "Bạn không có tiết dạy trong tháng này");
        if (command.total() != mine[0] || command.assistantTotal() != mine[1]) {
            throw conflict("TOTAL_CHANGED", "Số tiết đã thay đổi, tải lại bảng công trước khi xác nhận");
        }
        TkTeachingConfirmation row = row(tenantId, month, userId);
        long version = row.getVersion() == null ? 0 : row.getVersion();
        if (command.expectedVersion() != version) throw conflict("STALE_VERSION", "Bảng công vừa được cập nhật, tải lại");
        row.setStatus("CONFIRMED");
        row.setConfirmedTotal(mine[0]);
        row.setConfirmedAssistantTotal(mine[1]);
        row.setConfirmedAt(clock.instant());
        row.setIssueNote(null);
        return TeachingHoursService.confirmationView(confirmations.saveAndFlush(row));
    }

    @Transactional
    public TeachingConfirmationView reportIssue(String tenantId, String userId, YearMonth month, ReportTeachingIssueCommand command) {
        String note = command == null || command.note() == null ? "" : command.note().strip();
        if (note.isEmpty()) throw new TimekeepingException(HttpStatus.BAD_REQUEST, "NOTE_REQUIRED", "Nhập ghi chú sai sót");
        if (note.length() > 1000) throw new TimekeepingException(HttpStatus.BAD_REQUEST, "TEXT_TOO_LONG", "Tối đa 1000 ký tự");
        requireOpen(tenantId, month);
        int[] mine = hours.liveTotals(tenantId, month, userId).getOrDefault(userId, new int[2]);
        TkTeachingConfirmation row = row(tenantId, month, userId);
        row.setStatus("ISSUE_REPORTED");
        // the totals the teacher disputed: a later timetable fix changes them and sends the teacher back to PENDING
        row.setConfirmedTotal(mine[0]);
        row.setConfirmedAssistantTotal(mine[1]);
        row.setConfirmedAt(null);
        row.setIssueNote(note);
        return TeachingHoursService.confirmationView(confirmations.saveAndFlush(row));
    }

    /**
     * Sends back to PENDING (and tells) every teacher whose confirmed or disputed totals no longer match the live
     * timetable. Safe to call any time; a closed month is never touched. Returns how many teachers were reset.
     */
    @Transactional
    public int recheck(String tenantId, YearMonth month) {
        if (hours.isClosed(tenantId, month)) return 0;
        Map<String, int[]> live = null;
        int reset = 0;
        for (TkTeachingConfirmation c : confirmations.findByTenantIdAndMonth(tenantId, month.toString())) {
            if ("PENDING".equals(c.getStatus())) continue;
            if (live == null) live = hours.liveTotals(tenantId, month);
            reset += resetIfMoved(tenantId, month, c, live.getOrDefault(c.getTeacherUserId(), new int[2])) ? 1 : 0;
        }
        return reset;
    }

    /**
     * {@link #recheck} for one teacher, for the read of their own sheet: only their confirmation is compared, with
     * their own (teacher-filtered) live totals, so the read never scans the tenant and never touches anyone else.
     * The other teachers are covered by the schedule listener's {@link #recheckOpenMonths} and by the admin sheet.
     */
    @Transactional
    public boolean recheckMine(String tenantId, String userId, YearMonth month) {
        if (hours.isClosed(tenantId, month)) return false;
        TkTeachingConfirmation c = confirmations.findByTenantIdAndMonthAndTeacherUserId(tenantId, month.toString(), userId)
                .orElse(null);
        if (c == null || "PENDING".equals(c.getStatus())) return false;
        return resetIfMoved(tenantId, month, c, hours.liveTotals(tenantId, month, userId).getOrDefault(userId, new int[2]));
    }

    /**
     * {@link #recheck} against totals the caller already holds (the admin sheet read the month's events anyway), so it
     * costs no second scan; it writes only when a confirmation really is stale.
     */
    @Transactional
    public int recheck(String tenantId, YearMonth month, Map<String, int[]> live) {
        int reset = 0;
        for (TkTeachingConfirmation c : confirmations.findByTenantIdAndMonth(tenantId, month.toString())) {
            if ("PENDING".equals(c.getStatus())) continue;
            reset += resetIfMoved(tenantId, month, c, live.getOrDefault(c.getTeacherUserId(), new int[2])) ? 1 : 0;
        }
        return reset;
    }

    /** Sends one confirmation back to PENDING (and tells the teacher) when {@code live} no longer matches it. */
    private boolean resetIfMoved(String tenantId, YearMonth month, TkTeachingConfirmation c, int[] live) {
        if (equal(c.getConfirmedTotal(), live[0]) && equal(c.getConfirmedAssistantTotal(), live[1])) return false;
        c.setStatus("PENDING");
        c.setConfirmedTotal(null);
        c.setConfirmedAssistantTotal(null);
        c.setConfirmedAt(null);
        c.setIssueNote(null);
        notify(tenantId, month, c.getTeacherUserId(), live);
        return true;
    }

    /** Every month that still holds a confirmation to protect; the schedule listener calls this on a timetable change. */
    @Transactional
    public int recheckOpenMonths(String tenantId) {
        int reset = 0;
        for (String month : confirmations.findMonthsWithConfirmations(tenantId)) {
            reset += recheck(tenantId, YearMonth.parse(month));
        }
        return reset;
    }

    @Transactional
    public void close(String tenantId, String actorId, YearMonth month) {
        // two admins closing together queue here; the second sees CLOSED and returns
        requests.lockSubmitsOf("teaching-month-close:" + tenantId + ':' + month);
        TkTeachingMonth row = months.findByTenantIdAndMonth(tenantId, month.toString()).orElse(null);
        if (row != null && "CLOSED".equals(row.getStatus())) return;
        if (!hours.ended(month)) throw conflict("MONTH_NOT_ENDED", "Tháng chưa kết thúc");
        recheck(tenantId, month);
        // one read feeds the check and the snapshot, so the frozen lines are the totals that were confirmed
        List<TeachingPeriod> events = hours.monthEvents(tenantId, month);
        Map<String, int[]> live = TeachingHoursService.totals(events);
        if (live.isEmpty()) throw conflict("NO_TEACHING_PERIODS", "Tháng không có tiết dạy để chốt");
        Map<String, TkTeachingConfirmation> byTeacher = new LinkedHashMap<>();
        confirmations.findByTenantIdAndMonth(tenantId, month.toString()).forEach(c -> byTeacher.put(c.getTeacherUserId(), c));
        long missing = live.entrySet().stream().filter(t -> {
            TkTeachingConfirmation c = byTeacher.get(t.getKey());
            return c == null || !"CONFIRMED".equals(c.getStatus())
                    || !equal(c.getConfirmedTotal(), t.getValue()[0]) || !equal(c.getConfirmedAssistantTotal(), t.getValue()[1]);
        }).count();
        if (missing > 0) throw conflict("NOT_ALL_CONFIRMED", "Còn " + missing + " giáo viên chưa xác nhận");
        snapshots.saveAll(hours.snapshot(tenantId, month, events));
        if (row == null) {
            row = new TkTeachingMonth();
            row.setTenantId(tenantId);
            row.setMonth(month.toString());
        }
        row.setStatus("CLOSED");
        row.setClosedAt(clock.instant());
        row.setClosedBy(actorId);
        months.save(row);
        TkAuditLog entry = new TkAuditLog();
        entry.setTenantId(tenantId);
        entry.setActorId(actorId);
        entry.setAction("CLOSE_TEACHING_MONTH");
        entry.setTargetType("TEACHING_MONTH");
        entry.setTargetId(month.toString());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("month", month.toString());
        detail.put("teachers", live.size());
        detail.put("mainPeriods", live.values().stream().mapToInt(t -> t[0]).sum());
        detail.put("assistantPeriods", live.values().stream().mapToInt(t -> t[1]).sum());
        entry.setDetail(detail);
        entry.setAt(clock.instant());
        audit.save(entry);
    }

    private void requireOpen(String tenantId, YearMonth month) {
        if (hours.isClosed(tenantId, month)) throw conflict("MONTH_CLOSED", "Tháng đã chốt");
    }

    private TkTeachingConfirmation row(String tenantId, YearMonth month, String userId) {
        return confirmations.findByTenantIdAndMonthAndTeacherUserId(tenantId, month.toString(), userId).orElseGet(() -> {
            TkTeachingConfirmation created = new TkTeachingConfirmation();
            created.setTenantId(tenantId);
            created.setMonth(month.toString());
            created.setTeacherUserId(userId);
            return created;
        });
    }

    private void notify(String tenantId, YearMonth month, String teacherId, int[] mine) {
        try {
            notifier.remind(tenantId, "system", "teaching-month-changed:" + month + ':' + teacherId + ':' + mine[0] + ':' + mine[1],
                    teacherId, "Bảng công tiết dạy đổi số tiết",
                    "Số tiết dạy tháng " + month + " của bạn đã thay đổi do lịch dạy đổi. Vui lòng kiểm tra và xác nhận lại.");
        } catch (RuntimeException ex) {
            log.warn("Teaching month notice failed for user {}: {}", teacherId, ex.getClass().getSimpleName());
        }
    }

    private static boolean equal(Integer stored, int live) {
        return stored != null && stored == live;
    }

    private static TimekeepingException conflict(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.CONFLICT, code, messageVi);
    }
}
