package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Request/response shapes of `/api/v1/timekeeping/requests` (leave, explanation, WFH, business trip). */
public final class TimekeepingRequestViews {

    private TimekeepingRequestViews() {}

    /**
     * A draft's fields. LEAVE/WFH/BUSINESS use {@code fromDate..toDate} (+ {@code part} for a single-day leave);
     * EXPLANATION uses {@code workDate}, {@code explanationReason} and {@code proposedOut}. There is no {@code days}:
     * the server counts it.
     */
    public record RequestCommand(
            RequestType type,
            LeaveKind leaveKind,
            LocalDate fromDate,
            LocalDate toDate,
            DayPart part,
            LocalDate workDate,
            LocalTime proposedOut,
            ExplanationReason explanationReason,
            String note) {}

    /** One approver of one step; a step held by several people (e.g. two vice principals) has one ref each. */
    public record ApproverRef(int stepNo, String resolver, String userId, String displayName, String title) {}

    public record RequestStepView(
            int stepNo, String resolver, String approverId, String approverName, StepStatus status, String actedBy,
            Instant actedAt, boolean onBehalf, String comment, Instant viewedAt, Instant activatedAt, Instant dueAt) {}

    /** Schedule evidence (D14): {@code ref} is the period start instant, {@code label} e.g. "07:30–08:15 · 7A · Toán". */
    public record EvidenceView(String kind, String ref, String label) {}

    public record RequestView(
            String id,
            String code,
            RequestType type,
            LeaveKind leaveKind,
            LocalDate fromDate,
            LocalDate toDate,
            DayPart part,
            BigDecimal days,
            LocalDate workDate,
            LocalTime proposedOut,
            ExplanationReason explanationReason,
            String note,
            RequestStatus status,
            Integer currentStep,
            Instant submittedAt,
            Instant lastRemindedAt,
            List<RequestStepView> steps,
            List<EvidenceView> evidence) {}

    public record RequestPage(List<RequestView> items, int page, int size, long total) {}

    /** {@code balanceBefore} already subtracts other pending annual leave; for non-annual kinds before == after. */
    public record LeavePreview(
            BigDecimal days, BigDecimal balanceBefore, BigDecimal balanceAfter, List<PeriodView> affectedPeriods,
            List<ApproverRef> approvers) {}

    public record ExplanationContext(LocalTime suggestedOut, int remaining, List<EvidenceView> evidence) {}
}
