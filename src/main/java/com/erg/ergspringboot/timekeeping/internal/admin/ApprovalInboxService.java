package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.ApprovalBox;
import com.erg.ergspringboot.timekeeping.DueFilter;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.StepStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalDetail;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalItem;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ApprovalPage;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.BulkApproveResult;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.MonthContext;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Skipped;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TeammateLeave;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import com.erg.ergspringboot.timekeeping.internal.request.AffectedPeriodsService;
import com.erg.ergspringboot.timekeeping.internal.request.ApprovalService;
import com.erg.ergspringboot.timekeeping.internal.request.LeaveBalanceService;
import com.erg.ergspringboot.timekeeping.internal.request.RequestViewAssembler;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStep;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A03 approval inbox. A request is visible to someone who is (or was) one of its approvers, or to a
 * timekeeping.manage holder; anyone else gets 404 for its id. Acting: a current-step approver acts for themselves;
 * a manage holder who is not acts on behalf (ApprovalService records and audits it). Bulk approve takes each
 * request in its own transaction, so one refusal or lock conflict never undoes the others.
 */
@Service
public class ApprovalInboxService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalInboxService.class);
    private static final int BULK_MAX = 50;
    private static final Duration SOON = Duration.ofHours(24);
    private static final Instant EPOCH = Instant.EPOCH;

    private final TkRequestRepository requests;
    private final TkRequestStepRepository steps;
    private final TkDayRepository days;
    private final EmployeeProfileRepository profiles;
    private final RequestViewAssembler views;
    private final ApprovalService approvals;
    private final LeaveBalanceService balances;
    private final TeacherPeriodReader periods;
    private final ErgPolicyService policy;
    private final AdminSupport support;
    private final TransactionTemplate ownTransaction;

    ApprovalInboxService(TkRequestRepository requests, TkRequestStepRepository steps, TkDayRepository days,
            EmployeeProfileRepository profiles, RequestViewAssembler views, ApprovalService approvals,
            LeaveBalanceService balances, TeacherPeriodReader periods, ErgPolicyService policy, AdminSupport support,
            PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.steps = steps;
        this.days = days;
        this.profiles = profiles;
        this.views = views;
        this.approvals = approvals;
        this.balances = balances;
        this.periods = periods;
        this.policy = policy;
        this.support = support;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public ApprovalPage list(ErgPrincipal me, ApprovalBox box, RequestType type, String schoolId, DueFilter due,
            int page, int size) {
        ApprovalBox tab = box == null ? ApprovalBox.MINE : box;
        if (tab == ApprovalBox.ALL && !manages(me)) throw new AccessDeniedException("Chỉ quản trị xem được tất cả đơn");
        if (schoolId != null) support.requireSchool(me.tenantId(), schoolId);
        Instant now = support.clock().instant();
        Instant dueFrom = due == DueFilter.SOON ? now : EPOCH;
        Instant dueTo = due == DueFilter.SOON ? now.plus(SOON) : now;
        int pageSize = Math.max(1, Math.min(size, 100));
        Page<TkRequest> found = requests.findInbox(me.tenantId(), me.userId(), tab == ApprovalBox.MINE,
                tab == ApprovalBox.DONE, type != null, type == null ? RequestType.LEAVE : type, schoolId != null,
                schoolId, due != null, dueFrom, dueTo,
                PageRequest.of(Math.max(0, page), pageSize, Sort.by(Sort.Direction.DESC, "submittedAt", "id")));
        return new ApprovalPage(items(me.tenantId(), found.getContent(), now), found.getNumber(), pageSize,
                found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ApprovalDetail detail(ErgPrincipal me, String requestId) {
        String tenantId = me.tenantId();
        TkRequest r = visible(me, requestId);
        List<TkRequestStep> all = steps.findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(tenantId, requestId);
        boolean approver = currentApprover(r, all, me.userId());
        boolean manager = manages(me);
        boolean open = r.getStatus() == RequestStatus.PENDING && !me.userId().equals(r.getUserId());
        LocalDate from = r.getType() == RequestType.EXPLANATION ? r.getWorkDate() : r.getFromDate();
        LocalDate to = r.getType() == RequestType.EXPLANATION ? r.getWorkDate() : r.getToDate();

        List<PeriodView> affected = r.getType() == RequestType.LEAVE || r.getType() == RequestType.BUSINESS
                ? periods.read(tenantId, r.getUserId(), from, to).map(list -> AffectedPeriodsService
                        .inPart(list, r.getPart()).stream()
                        .map(p -> new PeriodView(p.start(), p.end(), p.className(), p.subject(), p.room())).toList())
                        .orElse(List.of())
                : List.of();
        YearMonth month = YearMonth.from(from);
        MonthContext context = new MonthContext(
                (int) days.countByTenantIdAndUserIdAndLateMinutesGreaterThanAndWorkDateBetween(tenantId, r.getUserId(),
                        0, month.atDay(1), month.atEndOfMonth()),
                (int) requests.countApprovedExplanations(tenantId, r.getUserId(), month.atDay(1), month.atEndOfMonth()));
        return new ApprovalDetail(items(tenantId, List.of(r), support.clock().instant()).get(0), affected, context,
                teammatesOnLeave(r, from, to), open && (approver || manager), open && !approver && manager);
    }

    public RequestView approve(ErgPrincipal me, String requestId, String comment) {
        TkRequest r = visible(me, requestId);
        approvals.approveStep(me.tenantId(), requestId, me, onBehalf(me, r), comment);
        return view(me.tenantId(), requestId);
    }

    public RequestView reject(ErgPrincipal me, String requestId, String comment) {
        TkRequest r = visible(me, requestId);
        approvals.rejectStep(me.tenantId(), requestId, me, onBehalf(me, r), comment);
        return view(me.tenantId(), requestId);
    }

    /**
     * Approves each request in its own transaction. Skipped (with a Vietnamese reason): not found / not visible,
     * not pending, not the caller's step, a locked month, or annual leave beyond the balance. At most 50 ids.
     */
    public BulkApproveResult bulkApprove(ErgPrincipal me, List<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty() || requestIds.size() > BULK_MAX) {
            throw AdminSupport.bad("INVALID_BULK", "Chọn từ 1 đến " + BULK_MAX + " đơn");
        }
        List<String> approved = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (String id : new LinkedHashSet<>(requestIds)) {
            try {
                ownTransaction.executeWithoutResult(status -> {
                    TkRequest r = visible(me, id);
                    requireBalance(r);
                    approvals.approveStep(me.tenantId(), id, me, onBehalf(me, r), null);
                });
                approved.add(id);
            } catch (AccessDeniedException denied) {
                skipped.add(new Skipped(id, "Bạn không phải người duyệt bước này"));
            } catch (ApiException refused) {
                skipped.add(new Skipped(id, refused.getMessage()));
            } catch (RuntimeException failed) {
                log.warn("Bulk approve failed for request {}: {}", id, failed.getClass().getSimpleName());
                skipped.add(new Skipped(id, "Không duyệt được đơn này"));
            }
        }
        return new BulkApproveResult(approved, skipped);
    }

    private TkRequest visible(ErgPrincipal me, String requestId) {
        String tenantId = me.tenantId();
        TkRequest r = requests.findByTenantIdAndId(tenantId, requestId)
                .filter(found -> found.getStatus() != RequestStatus.DRAFT)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy đơn"));
        boolean involved = steps.existsByTenantIdAndRequestIdAndApproverId(tenantId, requestId, me.userId())
                || steps.existsByTenantIdAndRequestIdAndActedBy(tenantId, requestId, me.userId());
        if (!involved && !manages(me)) throw AdminSupport.notFound("Không tìm thấy đơn");
        return r;
    }

    /** On behalf only when the caller is not a waiting approver of the current step (ApprovalService checks manage). */
    private boolean onBehalf(ErgPrincipal me, TkRequest r) {
        return !currentApprover(r, steps.findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(me.tenantId(), r.getId()),
                me.userId());
    }

    private static boolean currentApprover(TkRequest r, List<TkRequestStep> all, String userId) {
        return all.stream().anyMatch(s -> Objects.equals(s.getStepNo(), r.getCurrentStep())
                && s.getStatus() == StepStatus.WAITING && userId.equals(s.getApproverId()));
    }

    private boolean manages(ErgPrincipal me) {
        return policy.isAllowedAny(me, null, me.tenantId(), List.of(AdminSupport.MANAGE));
    }

    /** Annual leave must still fit the balance of every year it touches (other pending leave included). */
    private void requireBalance(TkRequest r) {
        if (r.getType() != RequestType.LEAVE || r.getLeaveKind() != LeaveKind.ANNUAL) return;
        for (int year = r.getFromDate().getYear(); year <= r.getToDate().getYear(); year++) {
            LocalDate from = r.getFromDate().getYear() == year ? r.getFromDate() : LocalDate.of(year, 1, 1);
            LocalDate to = r.getToDate().getYear() == year ? r.getToDate() : LocalDate.of(year, 12, 31);
            BigDecimal needed = balances.countDays(r.getTenantId(), r.getSchoolId(), from, to, r.getPart());
            if (balances.availableAnnual(r.getTenantId(), r.getUserId(), year, r.getId()).compareTo(needed) < 0) {
                throw new TimekeepingException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "INSUFFICIENT_BALANCE", "Không đủ phép năm");
            }
        }
    }

    private List<TeammateLeave> teammatesOnLeave(TkRequest r, LocalDate from, LocalDate to) {
        String tenantId = r.getTenantId();
        List<TkRequest> others = requests.findOthersLeaveOverlapping(tenantId, r.getSchoolId(), r.getUserId(), from, to);
        if (others.isEmpty()) return List.of();
        Set<String> ids = new HashSet<>();
        ids.add(r.getUserId());
        others.forEach(o -> ids.add(o.getUserId()));
        Map<String, EmployeeProfile> profileOf = profiles.findByTenantIdAndUserIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(EmployeeProfile::getUserId, Function.identity(), (a, b) -> a));
        EmployeeProfile mine = profileOf.get(r.getUserId());
        if (mine == null) return List.of();
        List<TkRequest> team = others.stream().filter(o -> {
            EmployeeProfile p = profileOf.get(o.getUserId());
            return p != null && (mine.getDepartmentId() != null && mine.getDepartmentId().equals(p.getDepartmentId())
                    || mine.getManagerId() != null && mine.getManagerId().equals(p.getManagerId()));
        }).toList();
        Map<String, String> names = support.names(tenantId, team.stream().map(TkRequest::getUserId).toList());
        return team.stream().map(o -> new TeammateLeave(o.getUserId(), names.get(o.getUserId()), o.getFromDate(),
                o.getToDate(), o.getStatus())).toList();
    }

    private List<ApprovalItem> items(String tenantId, List<TkRequest> found, Instant now) {
        if (found.isEmpty()) return List.of();
        List<RequestView> requestViews = views.many(tenantId, found);
        Map<String, String> names = support.names(tenantId, found.stream().map(TkRequest::getUserId).toList());
        List<ApprovalItem> items = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            TkRequest r = found.get(i);
            RequestView v = requestViews.get(i);
            boolean overdue = r.getStatus() == RequestStatus.PENDING && v.steps().stream()
                    .anyMatch(s -> Objects.equals(s.stepNo(), r.getCurrentStep()) && s.status() == StepStatus.WAITING
                            && s.dueAt() != null && s.dueAt().isBefore(now));
            items.add(new ApprovalItem(v, names.get(r.getUserId()), r.getSchoolId(), overdue));
        }
        return items;
    }

    private RequestView view(String tenantId, String requestId) {
        return views.one(requests.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> AdminSupport.notFound("Không tìm thấy đơn")));
    }
}
