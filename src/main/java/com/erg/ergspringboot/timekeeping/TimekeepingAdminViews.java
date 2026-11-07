package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Request/response shapes of `/api/v1/timekeeping/admin` and `/api/v1/timekeeping/approvals`. */
public final class TimekeepingAdminViews {

    private TimekeepingAdminViews() {}

    // ---- A01 today

    /** One 15-minute bucket starting at {@code time} (local Vietnam time): counted IN and OUT punches. */
    public record Bucket(LocalTime time, int in, int out) {}

    /** {@code kind}: NOT_PUNCHED_PERIOD_SOON (target = user), PUNCH_REVIEW (target = punch), REQUEST_OVERDUE. */
    public record ActionItem(String kind, String targetId, String userId, String userName, String label) {}

    public record RecentPunch(
            String id, String code, String userId, String userName, String schoolId, String siteName, Instant time,
            PunchDirection direction, PunchStatus status, boolean remote, boolean hasPhoto) {}

    /** Per school ("cơ sở"). */
    public record SiteCount(String schoolId, String name, int total, int present, int late) {}

    public record TodayDashboard(
            LocalDate date, int total, int present, int late, int lateMinutesTotal, int notPunched,
            int notPunchedWithPeriodSoon, int leave, int business, long pendingRequests, long overdueRequests,
            List<Bucket> inBuckets, List<ActionItem> actionItems, List<RecentPunch> recentPunches,
            List<SiteCount> bySite) {}

    public record RemindCommand(List<String> userIds) {}

    public record RemindResult(int sent, int skipped) {}

    // ---- A02 timesheet

    /** {@code code}: X, M, ½, P, Ô, CT, KC, KP, L (holiday) or "" (no shift / no row). */
    public record DayCell(int d, String code) {}

    public record TimesheetRow(
            String userId, String name, String employeeCode, String team, List<DayCell> days, BigDecimal work,
            int late, BigDecimal leave, BigDecimal kpKc, int periods, int deductMinutes) {}

    public record TimesheetIssues(long missingDays, long pendingRequests) {}

    public record Timesheet(
            String schoolId, String period, int standardDays, PeriodStage stage, LocalDate reviewUntil,
            TimesheetIssues issues, List<TimesheetRow> rows, int page, int size, long total) {}

    public record PeriodLockCommand(String schoolId, String reason) {}

    public record PeriodLockView(String schoolId, String period, PeriodStage stage, Instant lockedAt, String lockedBy,
            Instant reopenedAt, String reopenedBy) {}

    public record PunchReviewCommand(ReviewDecision decision, String comment) {}

    // ---- A03 approvals

    public record ApprovalItem(RequestView request, String requesterName, String schoolId, boolean overdue) {}

    public record ApprovalPage(List<ApprovalItem> items, int page, int size, long total) {}

    /** "Trong tháng: n lần đi muộn, n giải trình đã duyệt" for the requester's month of the request. */
    public record MonthContext(int lateCount, int approvedExplanations) {}

    public record TeammateLeave(String userId, String name, LocalDate fromDate, LocalDate toDate, RequestStatus status) {}

    /** {@code actingOnBehalf}: the caller is not a current-step approver and would act as manager (audited). */
    public record ApprovalDetail(
            ApprovalItem item, List<PeriodView> affectedPeriods, MonthContext context, List<TeammateLeave> teammatesOnLeave,
            boolean canAct, boolean actingOnBehalf) {}

    public record ApprovalComment(String comment) {}

    public record BulkApproveCommand(List<String> requestIds) {}

    public record Skipped(String id, String reason) {}

    public record BulkApproveResult(List<String> approved, List<Skipped> skipped) {}

    // ---- A04 configuration

    public record SiteCommand(String schoolId, String name, String address, BigDecimal lat, BigDecimal lng,
            Integer radiusM, Boolean active) {}

    public record SiteConfig(String id, String schoolId, String name, String address, BigDecimal lat, BigDecimal lng,
            int radiusM, boolean active) {}

    public record ShiftCommand(String schoolId, String name, LocalTime startTime, LocalTime endTime,
            BigDecimal workUnits, List<Integer> weekdays, Boolean active) {}

    public record ShiftConfig(String id, String schoolId, String name, LocalTime startTime, LocalTime endTime,
            BigDecimal workUnits, List<Integer> weekdays, boolean active) {}

    /** {@code approvalChains}: {"LEAVE":[{"minDays":0,"steps":["MANAGER","SCHOOL_ROLE:VICE_PRINCIPAL"]}],...}. */
    public record PolicyCommand(Integer lateToleranceMin, Integer lateBlockMin, Boolean requireFace,
            Boolean allowOutside, Integer explanationsPerMonth, Integer explanationWindowDays, Integer annualLeaveDays,
            Integer seniorityStepYears, Integer lockDayOfMonth, Map<String, Object> approvalChains) {}

    /** {@code id} is null for the built-in defaults (nothing configured yet). */
    public record PolicyConfig(String id, String schoolId, LocalDate effectiveFrom, int lateToleranceMin,
            int lateBlockMin, boolean requireFace, boolean allowOutside, int explanationsPerMonth,
            int explanationWindowDays, int annualLeaveDays, int seniorityStepYears, int lockDayOfMonth,
            Map<String, Object> approvalChains) {}

    /** {@code next}: the version scheduled for the next period, if any. */
    public record PolicyVersions(PolicyConfig current, PolicyConfig next) {}

    /** {@code schoolId} null = every school of the tenant. */
    public record HolidayCommand(String schoolId, LocalDate date, String name) {}

    public record HolidayConfig(String id, String schoolId, LocalDate date, String name) {}

    public record RemoteRuleCommand(String schoolId, RemoteRuleKind kind, List<String> userIds, Integer weekday,
            String shiftId, Boolean active) {}

    public record RemoteRuleConfig(String id, String schoolId, RemoteRuleKind kind, List<String> userIds,
            Integer weekday, String shiftId, boolean active) {}
}
