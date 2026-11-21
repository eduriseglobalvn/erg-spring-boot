package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.ReviewDecision;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.PunchReviewCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RecentPunch;
import com.erg.ergspringboot.timekeeping.internal.SchoolScope;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A manager's decision on a FLAGGED / PENDING_REVIEW punch: ACCEPT makes it VALID, REJECT makes it REJECTED (a
 * comment is required) and the day is rebuilt without it. Audited; refused in a locked month.
 */
@Service
public class PunchReviewService {

    private final TkPunchRepository punches;
    private final DayAggregationService days;
    private final WorkContextResolver context;
    private final AdminSupport support;
    private final TeacherPeriodReader calendar;
    private final TransactionTemplate write;

    PunchReviewService(TkPunchRepository punches, DayAggregationService days, WorkContextResolver context,
            AdminSupport support, TeacherPeriodReader calendar, PlatformTransactionManager transactionManager) {
        this.calendar = calendar;
        this.write = new TransactionTemplate(transactionManager);
        this.punches = punches;
        this.days = days;
        this.context = context;
        this.support = support;
    }

    public RecentPunch review(String tenantId, String actorId, String punchId, PunchReviewCommand command) {
        return review(tenantId, SchoolScope.TENANT, actorId, punchId, command);
    }

    /** A scoped caller reviews only punches of their schools (403 otherwise). */
    public RecentPunch review(String tenantId, SchoolScope scope, String actorId, String punchId,
            PunchReviewCommand command) {
        if (command == null || command.decision() == null) throw AdminSupport.bad("DECISION_REQUIRED", "Chọn quyết định");
        String comment = command.comment() == null || command.comment().isBlank() ? null : command.comment().strip();
        if (command.decision() == ReviewDecision.REJECT && comment == null) {
            throw AdminSupport.bad("COMMENT_REQUIRED", "Nhập lý do từ chối");
        }
        if (comment != null && comment.length() > 1000) throw AdminSupport.bad("NOTE_TOO_LONG", "Ghi chú tối đa 1000 ký tự");
        // the day's periods are read before the transaction opens: no second pooled connection inside it
        Optional<List<TeacherPeriod>> dayPeriods = punches.findByIdAndTenantId(punchId, tenantId)
                .map(p -> calendar.read(tenantId, p.getUserId(), p.getWorkDate(), p.getWorkDate())).orElse(null);
        return write.execute(status -> reviewInTransaction(tenantId, scope, actorId, punchId, command, comment, dayPeriods));
    }

    private RecentPunch reviewInTransaction(String tenantId, SchoolScope scope, String actorId, String punchId,
            PunchReviewCommand command, String comment, Optional<List<TeacherPeriod>> dayPeriods) {
        TkPunch punch = punches.findByIdAndTenantId(punchId, tenantId)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy lượt chấm"));
        scope.require(punch.getSchoolId());
        PunchStatus before = punch.getStatus();
        if (before != PunchStatus.FLAGGED && before != PunchStatus.PENDING_REVIEW) {
            throw AdminSupport.conflict("NOT_REVIEWABLE", "Lượt chấm này không cần duyệt");
        }
        context.requireOpen(tenantId, punch.getSchoolId(), punch.getWorkDate());
        boolean accept = command.decision() == ReviewDecision.ACCEPT;
        punch.setStatus(accept ? PunchStatus.VALID : PunchStatus.REJECTED);
        punch.setReviewDecision(command.decision());
        punch.setReviewedBy(actorId);
        punch.setReviewedAt(support.clock().instant());
        punch.setReviewNote(comment);
        punches.flush(); // the aggregation re-reads the day's punches
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("decision", command.decision().name());
        detail.put("before", before.name());
        detail.put("after", punch.getStatus().name());
        detail.put("comment", comment);
        support.audit(tenantId, actorId, "REVIEW_PUNCH", "PUNCH", punch.getId(), detail);
        if (dayPeriods == null) {
            days.recompute(tenantId, punch.getUserId(), punch.getWorkDate());
        } else {
            days.recompute(tenantId, punch.getUserId(), punch.getWorkDate(), dayPeriods);
        }
        String name = support.names(tenantId, List.of(punch.getUserId())).get(punch.getUserId());
        return TodayDashboardService.recent(punch, name, null);
    }
}
