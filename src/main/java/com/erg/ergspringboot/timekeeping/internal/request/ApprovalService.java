package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.StepStatus;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLog;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLogRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStep;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approve / reject one step (used by the approval inbox, T6). The actor must be one of the current step's approvers,
 * or act on behalf ({@code onBehalf}) while holding {@code timekeeping.manage} (deny overrides win); nobody acts on
 * their own request. Only the current step can be acted on and the request row is locked for the whole decision, so
 * of two simultaneous approvals one wins and the other gets 409. The last approval makes the request APPROVED and
 * recomputes every day it covers (a locked day is skipped and logged by the aggregation).
 */
@Service
public class ApprovalService {

    static final String MANAGE = "timekeeping.manage";

    private final TkRequestRepository requests;
    private final TkRequestStepRepository steps;
    private final TkAuditLogRepository audit;
    private final DayAggregationService days;
    private final ErgPolicyService policy;
    private final RequestNotifier notifier;
    private final WorkContextResolver context;
    private final Clock clock;

    ApprovalService(TkRequestRepository requests, TkRequestStepRepository steps, TkAuditLogRepository audit,
            DayAggregationService days, ErgPolicyService policy, RequestNotifier notifier, WorkContextResolver context,
            Clock clock) {
        this.context = context;
        this.requests = requests;
        this.steps = steps;
        this.audit = audit;
        this.days = days;
        this.policy = policy;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public void approveStep(String tenantId, String requestId, ErgPrincipal actor, boolean onBehalf, String comment) {
        act(tenantId, requestId, actor, onBehalf, comment, true);
    }

    /** {@code comment} is required (400). */
    @Transactional
    public void rejectStep(String tenantId, String requestId, ErgPrincipal actor, boolean onBehalf, String comment) {
        if (comment == null || comment.isBlank()) throw RequestRules.bad("COMMENT_REQUIRED", "Nhập lý do từ chối");
        act(tenantId, requestId, actor, onBehalf, comment, false);
    }

    private void act(String tenantId, String requestId, ErgPrincipal actor, boolean onBehalf, String comment,
            boolean approve) {
        if (comment != null && comment.length() > RequestRules.NOTE_MAX) {
            throw RequestRules.bad("NOTE_TOO_LONG", "Ghi chú tối đa 1000 ký tự");
        }
        if (actor == null || actor.userId() == null || !tenantId.equals(actor.tenantId())) {
            throw new AccessDeniedException("Bạn không có quyền duyệt đơn này");
        }
        TkRequest r = requests.findForUpdate(tenantId, requestId).orElseThrow(RequestService::notFound);
        if (r.getStatus() != RequestStatus.PENDING) throw new ConflictException("Đơn đã được xử lý");
        List<TkRequestStep> all = steps.findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(tenantId, requestId);
        int stepNo = r.getCurrentStep();
        List<TkRequestStep> current = all.stream()
                .filter(s -> s.getStepNo() == stepNo && s.getStatus() == StepStatus.WAITING).toList();
        if (current.isEmpty()) throw new ConflictException("Bước duyệt này đã được xử lý");
        TkRequestStep acted = current.stream().filter(s -> actor.userId().equals(s.getApproverId())).findFirst()
                .orElse(null);
        boolean forSomeoneElse = acted == null;
        if (forSomeoneElse) {
            if (!onBehalf || actor.userId().equals(r.getUserId())
                    || !policy.isAllowedAny(actor, null, tenantId, List.of(MANAGE))) {
                throw new AccessDeniedException("Bạn không phải người duyệt bước này");
            }
            acted = current.get(0);
        }
        if (approve) requireOpen(r); // rejecting changes no day, so it stays allowed in a locked month
        Instant now = clock.instant();
        acted.setStatus(approve ? StepStatus.APPROVED : StepStatus.REJECTED);
        acted.setActedBy(actor.userId());
        acted.setActedAt(now);
        acted.setOnBehalf(forSomeoneElse);
        acted.setComment(comment == null || comment.isBlank() ? null : comment.strip());
        for (TkRequestStep other : current) {
            if (other != acted) other.setStatus(StepStatus.SKIPPED); // first action wins for a shared step
        }
        if (forSomeoneElse) audit(r, actor, approve, stepNo, acted.getComment(), now);
        if (!approve) {
            all.stream().filter(s -> s.getStatus() == StepStatus.WAITING).forEach(s -> s.setStatus(StepStatus.SKIPPED));
            r.setStatus(RequestStatus.REJECTED);
            notifier.rejected(r, stepNo, acted.getComment());
            return;
        }
        Optional<Integer> next = all.stream()
                .filter(s -> s.getStepNo() > stepNo && s.getStatus() == StepStatus.WAITING)
                .map(TkRequestStep::getStepNo).min(Integer::compare);
        if (next.isPresent()) {
            int nextNo = next.get();
            r.setCurrentStep(nextNo);
            all.stream().filter(s -> s.getStepNo() == nextNo).forEach(s -> {
                s.setActivatedAt(now);
                s.setDueAt(now.plus(RequestService.STEP_DUE));
            });
            notifier.toApprovers(r, "STEP", nextNo, RequestService.waitingApprovers(all, nextNo));
            return;
        }
        r.setStatus(RequestStatus.APPROVED);
        requests.flush(); // the aggregation reads APPROVED requests
        for (LocalDate date : dates(r)) {
            days.recompute(tenantId, r.getUserId(), date);
        }
        notifier.approved(r, stepNo);
    }

    /** 409 PERIOD_LOCKED when any month the request covers is locked for the requester's school. */
    private void requireOpen(TkRequest r) {
        List<LocalDate> covered = dates(r);
        for (YearMonth m = YearMonth.from(covered.get(0)); !m.isAfter(YearMonth.from(covered.get(covered.size() - 1)));
                m = m.plusMonths(1)) {
            context.requireOpen(r.getTenantId(), r.getSchoolId(), m.atDay(1));
        }
    }

    private void audit(TkRequest r, ErgPrincipal actor, boolean approve, int stepNo, String comment, Instant now) {
        TkAuditLog row = new TkAuditLog();
        row.setTenantId(r.getTenantId());
        row.setActorId(actor.userId());
        row.setAction(approve ? "APPROVE_ON_BEHALF" : "REJECT_ON_BEHALF");
        row.setTargetType("REQUEST");
        row.setTargetId(r.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stepNo", stepNo);
        detail.put("comment", comment);
        row.setDetail(detail);
        row.setAt(now);
        audit.save(row);
    }

    private static List<LocalDate> dates(TkRequest r) {
        if (r.getType() == RequestType.EXPLANATION) return List.of(r.getWorkDate());
        return r.getFromDate().datesUntil(r.getToDate().plusDays(1)).toList();
    }
}
