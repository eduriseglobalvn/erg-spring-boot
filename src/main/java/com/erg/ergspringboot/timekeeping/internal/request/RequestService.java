package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.StepStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.ExplanationContext;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.LeavePreview;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestPage;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import com.erg.ergspringboot.timekeeping.internal.request.ApprovalChainResolver.ChainStep;
import com.erg.ergspringboot.timekeeping.internal.rules.ExplanationSuggester;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkCodeCounterRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestEvidence;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestEvidenceRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStep;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.utils.IdGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The requester's side of requests: drafts, submit (rules, approval chain, schedule evidence, step-1 notification),
 * cancel, remind and the read views. Every method is scoped to (tenant, requester): another person's request is
 * "not found", never "forbidden". Codes {@code DT-yyyy-nnnn} are taken when the draft is created (the column is
 * NOT NULL), so an abandoned draft leaves a gap in the sequence.
 */
@Service
public class RequestService {

    static final Duration STEP_DUE = Duration.ofDays(2);
    static final Duration REMIND_EVERY = Duration.ofHours(24);
    static final int MAX_PAGE_SIZE = 50;

    private final TkRequestRepository requests;
    private final TkRequestStepRepository steps;
    private final TkRequestEvidenceRepository evidence;
    private final TkCodeCounterRepository counters;
    private final RequestRules rules;
    private final ApprovalChainResolver chains;
    private final RequestNotifier notifier;
    private final RequestViewAssembler views;
    private final LeaveBalanceService balance;
    private final AffectedPeriodsService affected;
    private final TeacherPeriodReader periods;
    private final WorkContextResolver context;
    private final Clock clock;

    RequestService(TkRequestRepository requests, TkRequestStepRepository steps, TkRequestEvidenceRepository evidence,
            TkCodeCounterRepository counters, RequestRules rules, ApprovalChainResolver chains, RequestNotifier notifier,
            RequestViewAssembler views, LeaveBalanceService balance, AffectedPeriodsService affected,
            TeacherPeriodReader periods, WorkContextResolver context, Clock clock) {
        this.requests = requests;
        this.steps = steps;
        this.evidence = evidence;
        this.counters = counters;
        this.rules = rules;
        this.chains = chains;
        this.notifier = notifier;
        this.views = views;
        this.balance = balance;
        this.affected = affected;
        this.periods = periods;
        this.context = context;
        this.clock = clock;
    }

    @Transactional
    public RequestView create(String tenantId, String userId, RequestCommand command) {
        TkRequest r = new TkRequest();
        r.setTenantId(tenantId);
        r.setUserId(userId);
        r.setSchoolId(schoolOf(tenantId, userId));
        RequestRules.apply(r, command);
        int year = today().getYear();
        long n = counters.increment(IdGenerator.newId(), tenantId, "DT", String.valueOf(year));
        r.setCode("DT-%d-%04d".formatted(year, n));
        return views.one(requests.save(r));
    }

    @Transactional
    public RequestView update(String tenantId, String userId, String id, RequestCommand command) {
        TkRequest r = own(tenantId, userId, id);
        if (r.getStatus() != RequestStatus.DRAFT) throw conflict("NOT_DRAFT", "Chỉ sửa được đơn nháp");
        RequestRules.apply(r, command);
        return views.one(r);
    }

    @Transactional
    public RequestView submit(String tenantId, String userId, String id) {
        // one submit per person at a time: overlap, balance and explanation-limit checks read the others' rows
        requests.lockSubmitsOf(tenantId + ":" + userId);
        TkRequest r = own(tenantId, userId, id);
        if (r.getStatus() != RequestStatus.DRAFT) throw conflict("NOT_DRAFT", "Đơn đã được gửi");
        rules.checkForSubmit(r);
        List<ChainStep> chain = chains.resolve(tenantId, r.getSchoolId(), userId, r.getType(),
                r.getDays() == null ? BigDecimal.ZERO : r.getDays());
        ChainStep first = chain.stream().filter(s -> !s.skipped()).findFirst().orElseThrow(() ->
                RequestRules.unprocessable("NO_APPROVER", "Chưa có người duyệt cho đơn này"));
        Instant now = clock.instant();
        for (ChainStep step : chain) {
            if (step.skipped()) {
                steps.save(step(r, step, null, StepStatus.SKIPPED, null));
            }
            for (String approverId : step.approverIds()) {
                steps.save(step(r, step, approverId, StepStatus.WAITING, step == first ? now : null));
            }
        }
        r.setStatus(RequestStatus.PENDING);
        r.setCurrentStep(first.stepNo());
        r.setSubmittedAt(now);
        attachSchedule(r);
        notifier.toApprovers(r, "SUBMITTED", first.stepNo(), first.approverIds());
        return views.one(r);
    }

    /** A draft, or a pending request no approver has acted on yet; open steps are closed and their approvers told. */
    @Transactional
    public RequestView cancel(String tenantId, String userId, String id) {
        TkRequest r = own(tenantId, userId, id);
        if (r.getStatus() == RequestStatus.PENDING) {
            List<TkRequestStep> all = steps.findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(tenantId, id);
            if (all.stream().anyMatch(s -> s.getStatus() == StepStatus.APPROVED)) {
                throw conflict("CANNOT_CANCEL", "Không thể huỷ đơn đã có bước được duyệt");
            }
            notifier.cancelled(r, r.getCurrentStep(), waitingApprovers(all, r.getCurrentStep()));
            all.stream().filter(s -> s.getStatus() == StepStatus.WAITING).forEach(s -> s.setStatus(StepStatus.SKIPPED));
        } else if (r.getStatus() != RequestStatus.DRAFT) {
            throw conflict("CANNOT_CANCEL", "Không thể huỷ đơn này");
        }
        r.setStatus(RequestStatus.CANCELLED);
        return views.one(r);
    }

    @Transactional
    public RequestView remind(String tenantId, String userId, String id) {
        TkRequest r = own(tenantId, userId, id);
        if (r.getStatus() != RequestStatus.PENDING) throw conflict("NOT_PENDING", "Chỉ nhắc được đơn đang chờ duyệt");
        Instant now = clock.instant();
        if (r.getLastRemindedAt() != null && now.isBefore(r.getLastRemindedAt().plus(REMIND_EVERY))) {
            throw new TimekeepingException(HttpStatus.TOO_MANY_REQUESTS, "REMIND_TOO_SOON",
                    "Mỗi đơn chỉ nhắc duyệt một lần trong 24 giờ");
        }
        r.setLastRemindedAt(now);
        List<TkRequestStep> all = steps.findByTenantIdAndRequestIdOrderByStepNoAscApproverIdAsc(tenantId, id);
        notifier.remind(r, r.getCurrentStep(), now.getEpochSecond(), waitingApprovers(all, r.getCurrentStep()));
        return views.one(r);
    }

    @Transactional(readOnly = true)
    public RequestView get(String tenantId, String userId, String id) {
        return views.one(requests.findByTenantIdAndUserIdAndId(tenantId, userId, id).orElseThrow(RequestService::notFound));
    }

    /** Newest first; {@code size} is capped at 50. */
    @Transactional(readOnly = true)
    public RequestPage mine(String tenantId, String userId, RequestStatus status, int page, int size) {
        int capped = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageable = PageRequest.of(Math.max(0, page), capped,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<TkRequest> found = status == null
                ? requests.findByTenantIdAndUserId(tenantId, userId, pageable)
                : requests.findByTenantIdAndUserIdAndStatus(tenantId, userId, status, pageable);
        return new RequestPage(views.many(tenantId, found.getContent()), pageable.getPageNumber(), capped,
                found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public LeavePreview previewLeave(String tenantId, String userId, LocalDate from, LocalDate to, DayPart part,
            LeaveKind kind) {
        String schoolId = schoolOf(tenantId, userId);
        TkRequest probe = new TkRequest();
        RequestRules.apply(probe, new RequestCommand(RequestType.LEAVE, kind, from, to, part, null, null, null, null));
        BigDecimal days = balance.countDays(tenantId, schoolId, from, to, probe.getPart());
        // per calendar year as at submit; across year-end the year left with the least balance is shown
        RequestRules.YearBalance binding = rules.annualByYear(tenantId, userId, schoolId, from, to, probe.getPart(), null)
                .stream().min(Comparator.comparing(y -> y.available().subtract(kind == LeaveKind.ANNUAL
                        ? y.wanted() : BigDecimal.ZERO))).orElseThrow();
        BigDecimal before = binding.available();
        BigDecimal after = kind == LeaveKind.ANNUAL ? before.subtract(binding.wanted()) : before;
        List<PeriodView> hit = affected.affected(tenantId, userId, from, to, probe.getPart()).stream()
                .map(RequestService::period).toList();
        return new LeavePreview(days, before, after, hit,
                chains.refs(tenantId, chains.resolve(tenantId, schoolId, userId, RequestType.LEAVE, days)));
    }

    @Transactional(readOnly = true)
    public ExplanationContext explanationContext(String tenantId, String userId, LocalDate date) {
        String schoolId = schoolOf(tenantId, userId);
        List<TeacherPeriod> dayPeriods = periods.read(tenantId, userId, date, date).orElse(List.of());
        Optional<LocalTime> lastEnd = dayPeriods.stream().reduce((a, b) -> b)
                .map(p -> LocalTime.ofInstant(p.end(), WorkContextResolver.ZONE));
        LocalTime suggested = context.shiftFor(tenantId, schoolId, date)
                .map(TkShift::getEndTime).map(end -> ExplanationSuggester.suggestOut(end, lastEnd)).orElse(null);
        YearMonth month = YearMonth.from(date);
        long used = requests.countActiveExplanations(tenantId, userId, month.atDay(1), month.atEndOfMonth());
        int allowed = context.policyFor(tenantId, schoolId, date).getExplanationsPerMonth();
        return new ExplanationContext(suggested, (int) Math.max(0, allowed - used),
                dayPeriods.stream().map(p -> RequestViewAssembler.evidence(RequestViewAssembler.ref(p),
                        RequestViewAssembler.label(p))).toList());
    }

    /** D14: the teaching periods of the request's day(s) (half a day: that half), read once at submit. */
    private void attachSchedule(TkRequest r) {
        boolean explanation = r.getType() == RequestType.EXPLANATION;
        LocalDate from = explanation ? r.getWorkDate() : r.getFromDate();
        LocalDate to = explanation ? r.getWorkDate() : r.getToDate();
        List<TeacherPeriod> found = periods.read(r.getTenantId(), r.getUserId(), from, to).orElse(List.of());
        for (TeacherPeriod p : AffectedPeriodsService.inPart(found, r.getPart())) {
            TkRequestEvidence row = new TkRequestEvidence();
            row.setTenantId(r.getTenantId());
            row.setRequestId(r.getId());
            row.setRef(RequestViewAssembler.ref(p));
            row.setLabel(RequestViewAssembler.label(p));
            evidence.save(row);
        }
    }

    private static TkRequestStep step(TkRequest r, ChainStep chainStep, String approverId, StepStatus status,
            Instant activatedAt) {
        TkRequestStep row = new TkRequestStep();
        row.setTenantId(r.getTenantId());
        row.setRequestId(r.getId());
        row.setStepNo(chainStep.stepNo());
        row.setResolver(chainStep.resolver());
        row.setApproverId(approverId);
        row.setStatus(status);
        if (activatedAt != null) {
            row.setActivatedAt(activatedAt);
            row.setDueAt(activatedAt.plus(STEP_DUE));
        }
        return row;
    }

    static List<String> waitingApprovers(List<TkRequestStep> all, Integer stepNo) {
        return all.stream().filter(s -> s.getStepNo().equals(stepNo) && s.getStatus() == StepStatus.WAITING)
                .map(TkRequestStep::getApproverId).toList();
    }

    /** The caller's own request, row-locked for the change. */
    private TkRequest own(String tenantId, String userId, String id) {
        return requests.findOwnForUpdate(tenantId, userId, id).orElseThrow(RequestService::notFound);
    }

    private String schoolOf(String tenantId, String userId) {
        return context.schoolOf(tenantId, userId).orElseThrow(() ->
                RequestRules.unprocessable("NO_SCHOOL", "Bạn chưa thuộc trường nào có cấu hình chấm công"));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(WorkContextResolver.ZONE));
    }

    private static PeriodView period(TeacherPeriod p) {
        return new PeriodView(p.start(), p.end(), p.className(), p.subject(), p.room());
    }

    static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn");
    }

    private static TimekeepingException conflict(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.CONFLICT, code, messageVi);
    }
}
