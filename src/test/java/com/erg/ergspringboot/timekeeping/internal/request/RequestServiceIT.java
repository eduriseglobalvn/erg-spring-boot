package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.exception.ApiException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.ExplanationReason;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.StepStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestStepView;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/** Request rules, approval chain, approve/reject on real PostgreSQL. Clock: Thu 12/11/2026 08:00 VN. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresAvailable")
public class RequestServiceIT {

    static final Instant NOW = Instant.parse("2026-11-12T01:00:00Z");
    private static final String TENANT = "tk-req-it";
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final LocalDate TUE = LocalDate.of(2026, 11, 17);

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedTimekeepingClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @MockitoBean CalendarReadPort calendar;
    @MockitoBean NotificationAdminOperations notifications;
    @Autowired RequestService requests;
    @Autowired ApprovalService approvals;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @PersistenceContext EntityManager entityManager;

    private Fixture f;

    @BeforeEach
    void seed() {
        when(calendar.teacherPeriods(anyString(), anyString(), any(), any())).thenReturn(List.of(new TeacherPeriod(
                Instant.parse("2026-11-16T01:00:00Z"), Instant.parse("2026-11-16T01:45:00Z"), "7A", "Toán", "P201")));
        if (!TestTransaction.isActive()) return; // committed tests seed their own tenant
        f = Fixture.seed(jdbc, TENANT, "");
    }

    @Test
    void submittingTwoDaysOfLeaveBuildsTwoStepsACodeAndScheduleEvidence() {
        RequestView draft = requests.create(TENANT, f.teacher, leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));
        assertThat(draft.status()).isEqualTo(RequestStatus.DRAFT);
        assertThat(draft.code()).matches("DT-2026-\\d{4}");

        RequestView sent = requests.submit(TENANT, f.teacher, draft.id());

        assertThat(sent.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(sent.days()).isEqualByComparingTo("2");
        assertThat(sent.currentStep()).isEqualTo(1);
        assertThat(sent.submittedAt()).isEqualTo(NOW);
        assertThat(sent.steps()).extracting(RequestStepView::stepNo, RequestStepView::resolver, RequestStepView::approverId,
                RequestStepView::status).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "MANAGER", f.lead, StepStatus.WAITING),
                        org.assertj.core.groups.Tuple.tuple(2, "SCHOOL_ROLE:VICE_PRINCIPAL", f.vp, StepStatus.WAITING),
                        org.assertj.core.groups.Tuple.tuple(2, "SCHOOL_ROLE:VICE_PRINCIPAL", f.vp2, StepStatus.WAITING));
        assertThat(sent.steps().get(0).activatedAt()).isEqualTo(NOW);
        assertThat(sent.steps().get(0).dueAt()).isEqualTo(NOW.plus(Duration.ofDays(2)));
        assertThat(sent.steps().get(1).activatedAt()).isNull();
        assertThat(sent.evidence()).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo("SCHEDULE");
            assertThat(e.label()).contains("7A").contains("Toán");
        });
    }

    @Test
    void halfADayOfLeaveIsOneStep() {
        RequestView sent = submit(leave(LeaveKind.ANNUAL, MON, MON, DayPart.AM));

        assertThat(sent.days()).isEqualByComparingTo("0.5");
        assertThat(sent.steps()).singleElement().extracting(RequestStepView::approverId).isEqualTo(f.lead);
    }

    @Test
    void annualLeaveBeyondTheBalanceIsRefusedWithTheBalanceSentence() {
        approvedAnnual(LocalDate.of(2026, 3, 2), "12.5"); // 14 - 12.5 = 1.5

        assertError(() -> submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL)), HttpStatus.UNPROCESSABLE_CONTENT,
                "Phép năm còn 1,5 ngày, không đủ cho 2 ngày");
        // other kinds do not use the annual balance
        assertThat(submit(leave(LeaveKind.UNPAID, MON, TUE, DayPart.FULL)).status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void pendingAnnualLeaveCountsSoTwoRequestsCannotOverdraw() {
        approvedAnnual(LocalDate.of(2026, 3, 2), "11"); // 3 left
        submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));

        assertError(() -> submit(leave(LeaveKind.ANNUAL, LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20),
                DayPart.FULL)), HttpStatus.UNPROCESSABLE_CONTENT, "Phép năm còn 1 ngày, không đủ cho 2 ngày");
    }

    @Test
    void fromAfterToIsABadRequest() {
        assertError(() -> requests.create(TENANT, f.teacher, leave(LeaveKind.ANNUAL, TUE, MON, DayPart.FULL)),
                HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        assertError(() -> requests.create(TENANT, f.teacher, leave(LeaveKind.ANNUAL, MON, TUE, DayPart.AM)),
                HttpStatus.BAD_REQUEST, null);
        assertError(() -> requests.create(TENANT, f.teacher, new RequestCommand(RequestType.WFH, null, MON, MON, null,
                null, null, null, " ")), HttpStatus.BAD_REQUEST, "Nhập lý do");
    }

    @Test
    void aRequestInALockedPeriodIsAConflict() {
        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by)"
                + " values (?, ?, ?, '2026-11', now(), 'admin')", UUID.randomUUID().toString(), TENANT, f.school);

        assertError(() -> submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL)), HttpStatus.CONFLICT, "Kỳ công đã chốt");
    }

    @Test
    void overlappingActiveRequestsAreAConflict() {
        submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));

        assertError(() -> submit(new RequestCommand(RequestType.WFH, null, TUE, TUE, null, null, null, null, "Ở nhà")),
                HttpStatus.CONFLICT, null);
        // AM and PM of one day do not overlap
        LocalDate wed = LocalDate.of(2026, 11, 18);
        submit(leave(LeaveKind.SICK, wed, wed, DayPart.AM));
        assertThat(submit(leave(LeaveKind.SICK, wed, wed, DayPart.PM)).status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void explanationRulesWindowMonthlyLimitAndProposedOut() {
        LocalDate yesterday = LocalDate.of(2026, 11, 11);
        LocalDate fourDaysAgo = LocalDate.of(2026, 11, 8);
        missingOut(yesterday);
        missingOut(fourDaysAgo);

        assertError(() -> submit(explain(fourDaysAgo, LocalTime.of(11, 30))), HttpStatus.UNPROCESSABLE_CONTENT,
                "Chỉ giải trình trong 3 ngày kể từ ngày thiếu");
        assertError(() -> submit(explain(LocalDate.of(2026, 11, 10), LocalTime.of(11, 30))),
                HttpStatus.UNPROCESSABLE_CONTENT, null); // not a missing-punch day
        assertError(() -> requests.create(TENANT, f.teacher, explain(yesterday, null)), HttpStatus.BAD_REQUEST,
                "Chọn giờ ra đề nghị");
        assertError(() -> submit(explain(yesterday, LocalTime.of(13, 0))), HttpStatus.UNPROCESSABLE_CONTENT, null);

        RequestView ok = submit(explain(yesterday, LocalTime.of(11, 30)));
        assertThat(ok.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(ok.steps()).singleElement().extracting(RequestStepView::approverId).isEqualTo(f.lead);
        assertThat(requests.explanationContext(TENANT, f.teacher, yesterday).remaining()).isEqualTo(2);

        for (int day = 2; day <= 3; day++) {
            jdbc.update("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, work_date, status)"
                    + " values (?, ?, ?, ?, ?, 'EXPLANATION', ?, 'APPROVED')", UUID.randomUUID().toString(), TENANT,
                    f.school, "DT-X-" + day, f.teacher, LocalDate.of(2026, 11, day));
        }
        missingOut(LocalDate.of(2026, 11, 10));
        assertError(() -> submit(explain(LocalDate.of(2026, 11, 10), LocalTime.of(11, 30))),
                HttpStatus.UNPROCESSABLE_CONTENT, "Đã dùng hết 3 lượt giải trình tháng này");
    }

    @Test
    void approvingTheLastStepApprovesRecomputesEachDayAndAnotherApprovalConflicts() {
        RequestView sent = submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));

        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal(f.vp), false, null))
                .isInstanceOf(AccessDeniedException.class); // step 2 is not active yet
        approvals.approveStep(TENANT, sent.id(), principal(f.lead), false, "Đồng ý");
        RequestView atTwo = requests.get(TENANT, f.teacher, sent.id());
        assertThat(atTwo.currentStep()).isEqualTo(2);
        assertThat(atTwo.steps().get(1).activatedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal(f.lead), false, null))
                .isInstanceOf(AccessDeniedException.class);

        approvals.approveStep(TENANT, sent.id(), principal(f.vp2), false, null);

        RequestView done = requests.get(TENANT, f.teacher, sent.id());
        assertThat(done.status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(done.steps()).extracting(RequestStepView::status)
                .containsExactly(StepStatus.APPROVED, StepStatus.SKIPPED, StepStatus.APPROVED);
        entityManager.flush();
        assertThat(jdbc.queryForList("select status from tk_days where tenant_id = ? and user_id = ? order by work_date",
                String.class, TENANT, f.teacher)).containsExactly("LEAVE", "LEAVE");
        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal(f.vp), false, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyAManageHolderMayActOnBehalfAndItIsAudited() {
        RequestView sent = submit(new RequestCommand(RequestType.WFH, null, MON, MON, null, null, null, null, "Ở nhà"));

        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal("stranger", "timekeeping.approve"),
                false, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal("stranger", "timekeeping.approve"),
                true, null)).isInstanceOf(AccessDeniedException.class);
        ErgPrincipal deniedAdmin = new ErgPrincipal("admin-x", "s", TENANT, List.of(), List.of("*"),
                List.of("timekeeping.manage"), List.of(), "STAFF", null, Map.of());
        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), deniedAdmin, true, null))
                .isInstanceOf(AccessDeniedException.class);

        approvals.approveStep(TENANT, sent.id(), principal("admin-1", "timekeeping.manage"), true, "Duyệt thay");

        RequestView done = requests.get(TENANT, f.teacher, sent.id());
        assertThat(done.status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(done.steps().get(0).onBehalf()).isTrue();
        assertThat(done.steps().get(0).actedBy()).isEqualTo("admin-1");
        entityManager.flush();
        Map<String, Object> audit = jdbc.queryForMap("select actor_id, action, detail::text as detail from tk_audit_log"
                + " where tenant_id = ? and target_id = ?", TENANT, sent.id());
        assertThat(audit).containsEntry("actor_id", "admin-1").containsEntry("action", "APPROVE_ON_BEHALF");
        assertThat(audit.get("detail").toString()).contains("Duyệt thay");
    }

    @Test
    void rejectNeedsACommentAndClosesTheRemainingSteps() {
        RequestView sent = submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));

        assertError(() -> approvals.rejectStep(TENANT, sent.id(), principal(f.lead), false, "  "),
                HttpStatus.BAD_REQUEST, null);
        approvals.rejectStep(TENANT, sent.id(), principal(f.lead), false, "Trùng lịch thi");

        RequestView rejected = requests.get(TENANT, f.teacher, sent.id());
        assertThat(rejected.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected.steps()).extracting(RequestStepView::status)
                .containsExactly(StepStatus.REJECTED, StepStatus.SKIPPED, StepStatus.SKIPPED);
        assertThatThrownBy(() -> approvals.rejectStep(TENANT, sent.id(), principal(f.lead), false, "x"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelIsOnlyForDraftsAndUntouchedPendingRequests() {
        RequestView draft = requests.create(TENANT, f.teacher, leave(LeaveKind.ANNUAL, MON, MON, DayPart.FULL));
        assertThat(requests.cancel(TENANT, f.teacher, draft.id()).status()).isEqualTo(RequestStatus.CANCELLED);

        RequestView pending = submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));
        RequestView cancelled = requests.cancel(TENANT, f.teacher, pending.id());
        assertThat(cancelled.status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(cancelled.steps()).extracting(RequestStepView::status).containsOnly(StepStatus.SKIPPED);

        RequestView started = submit(leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL));
        approvals.approveStep(TENANT, started.id(), principal(f.lead), false, null);
        assertError(() -> requests.cancel(TENANT, f.teacher, started.id()), HttpStatus.CONFLICT, null);
        assertError(() -> requests.update(TENANT, f.teacher, started.id(), leave(LeaveKind.SICK, MON, MON, DayPart.FULL)),
                HttpStatus.CONFLICT, null);
    }

    @Test
    void remindIsThrottledToOncePerDayAndOwnerOnly() {
        RequestView sent = submit(leave(LeaveKind.ANNUAL, MON, MON, DayPart.FULL));

        assertThat(requests.remind(TENANT, f.teacher, sent.id()).lastRemindedAt()).isEqualTo(NOW);
        assertError(() -> requests.remind(TENANT, f.teacher, sent.id()), HttpStatus.TOO_MANY_REQUESTS, null);
        assertThatThrownBy(() -> requests.remind(TENANT, f.lead, sent.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherUsersRequestDoesNotExistForMe() {
        RequestView mine = requests.create(TENANT, f.teacher, leave(LeaveKind.ANNUAL, MON, MON, DayPart.FULL));
        requests.submit(TENANT, f.teacher, requests.create(TENANT, f.teacher,
                leave(LeaveKind.SICK, TUE, TUE, DayPart.FULL)).id());

        assertThatThrownBy(() -> requests.get(TENANT, f.lead, mine.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> requests.get("other-tenant", f.teacher, mine.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(requests.mine(TENANT, f.teacher, null, 0, 20).total()).isEqualTo(2);
        assertThat(requests.mine(TENANT, f.teacher, RequestStatus.PENDING, 0, 20).items()).singleElement()
                .extracting(RequestView::leaveKind).isEqualTo(LeaveKind.SICK);
        assertThat(requests.mine(TENANT, f.lead, null, 0, 20).total()).isZero();
        assertThat(requests.mine(TENANT, f.teacher, null, 0, 500).size()).isEqualTo(50);
    }

    @Test
    void approvingIntoAMonthLockedAfterSubmitIsRefusedButRejectingStillWorks() {
        RequestView sent = submit(leave(LeaveKind.ANNUAL, MON, MON, DayPart.FULL));
        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by)"
                + " values (?, ?, ?, '2026-11', now(), 'admin')", UUID.randomUUID().toString(), TENANT, f.school);

        assertError(() -> approvals.approveStep(TENANT, sent.id(), principal(f.lead), false, null), HttpStatus.CONFLICT,
                "Kỳ công đã chốt");

        RequestView unchanged = requests.get(TENANT, f.teacher, sent.id());
        assertThat(unchanged.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(unchanged.currentStep()).isEqualTo(1);
        assertThat(unchanged.steps()).hasSize(3).allSatisfy(s -> {
            assertThat(s.status()).isEqualTo(StepStatus.WAITING);
            assertThat(s.actedBy()).isNull();
        });
        approvals.rejectStep(TENANT, sent.id(), principal(f.lead), false, "Kỳ đã chốt");
        assertThat(requests.get(TENANT, f.teacher, sent.id()).status()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void aManageHolderCannotApproveTheirOwnRequestOnBehalf() {
        RequestView sent = submit(new RequestCommand(RequestType.WFH, null, MON, MON, null, null, null, null, "Ở nhà"));

        assertThatThrownBy(() -> approvals.approveStep(TENANT, sent.id(), principal(f.teacher, "timekeeping.manage"),
                true, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(requests.get(TENANT, f.teacher, sent.id()).status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void nobodyToApproveIsRefused() {
        Fixture lonely = Fixture.seed(jdbc, TENANT, "x");
        jdbc.update("update employee_profiles set manager_id = null where user_id = ?", lonely.teacher);
        jdbc.update("delete from user_roles where user_id in (?, ?)", lonely.vp, lonely.vp2);

        assertError(() -> requests.submit(TENANT, lonely.teacher, requests.create(TENANT, lonely.teacher,
                leave(LeaveKind.SICK, MON, MON, DayPart.FULL)).id()), HttpStatus.UNPROCESSABLE_CONTENT,
                "Chưa có người duyệt cho đơn này");
    }

    @Test
    void previewLeaveShowsDaysBalanceAffectedPeriodsAndApprovers() {
        approvedAnnual(LocalDate.of(2026, 3, 2), "5.5");

        var preview = requests.previewLeave(TENANT, f.teacher, MON, TUE, DayPart.FULL, LeaveKind.ANNUAL);

        assertThat(preview.days()).isEqualByComparingTo("2");
        assertThat(preview.balanceBefore()).isEqualByComparingTo("8.5");
        assertThat(preview.balanceAfter()).isEqualByComparingTo("6.5");
        assertThat(preview.affectedPeriods()).singleElement().extracting(p -> p.className()).isEqualTo("7A");
        assertThat(preview.approvers()).extracting(a -> a.userId()).containsExactly(f.lead, f.vp, f.vp2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoApproversActingAtOnceExactlyOneWinsAndNotificationsAreSentOnceAfterCommit() throws Exception {
        String tenant = "tk-race-" + UUID.randomUUID().toString().substring(0, 8);
        String sfx = tenant.substring(8, 12);
        Fixture race = transactions.execute(status -> Fixture.seed(jdbc, tenant, sfx));
        try {
            String id = transactions.execute(status -> requests.submit(tenant, race.teacher,
                    requests.create(tenant, race.teacher, leave(LeaveKind.ANNUAL, MON, TUE, DayPart.FULL)).id()).id());
            transactions.executeWithoutResult(status -> approvals.approveStep(tenant, id, principalIn(tenant, race.lead), false, null));

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> results = new ArrayList<>();
            for (String vp : List.of(race.vp, race.vp2)) {
                Callable<String> act = () -> {
                    start.await();
                    try {
                        transactions.executeWithoutResult(status -> approvals.approveStep(tenant, id, principalIn(tenant, vp),
                                false, null));
                        return "approved";
                    } catch (RuntimeException lost) {
                        return lost.getClass().getSimpleName() + ": " + lost.getMessage();
                    }
                };
                results.add(pool.submit(act));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) outcomes.add(result.get());
            pool.shutdown();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(outcomes).containsExactlyInAnyOrder("approved", "ConflictException: Đơn đã được xử lý");
            assertThat(jdbc.queryForObject("select status from tk_requests where id = ?", String.class, id))
                    .isEqualTo("APPROVED");
            assertThat(jdbc.queryForList("select status from tk_request_steps where request_id = ? and step_no = 2",
                    String.class, id)).containsExactlyInAnyOrder("APPROVED", "SKIPPED");
            verify(notifications, times(1)).send(eq(tenant), anyString(), argThat((AdminSendRequest r) ->
                    r.idempotencyKey().equals("tk-req:" + id + ":SUBMITTED:1") && r.userIds().equals(List.of(race.lead))));
            verify(notifications, times(1)).send(eq(tenant), anyString(), argThat((AdminSendRequest r) ->
                    r.idempotencyKey().equals("tk-req:" + id + ":STEP:2")
                            && r.userIds().containsAll(List.of(race.vp, race.vp2))));
            verify(notifications, times(1)).send(eq(tenant), anyString(), argThat((AdminSendRequest r) ->
                    r.idempotencyKey().equals("tk-req:" + id + ":APPROVED:2") && r.userIds().equals(List.of(race.teacher))));
        } finally {
            clearInvocations(notifications);
            // inside a transaction: the tenant-aware transaction manager makes RLS permissive for context-free work
            transactions.executeWithoutResult(status -> Fixture.purge(jdbc, tenant));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoOverlappingDraftsSubmittedAtOnceExactlyOnePasses() throws Exception {
        String tenant = "tk-race-" + UUID.randomUUID().toString().substring(0, 8);
        Fixture race = transactions.execute(status -> Fixture.seed(jdbc, tenant, tenant.substring(8, 12)));
        try {
            List<String> drafts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                drafts.add(transactions.execute(status -> requests.create(tenant, race.teacher,
                        leave(LeaveKind.SICK, MON, TUE, DayPart.FULL)).id()));
            }
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> results = new ArrayList<>();
            for (String draft : drafts) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        transactions.executeWithoutResult(status -> requests.submit(tenant, race.teacher, draft));
                        return "submitted";
                    } catch (ApiException lost) {
                        return lost.getCode();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) outcomes.add(result.get());
            pool.shutdown();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(outcomes).containsExactlyInAnyOrder("submitted", "REQUEST_OVERLAP");
        } finally {
            transactions.executeWithoutResult(status -> Fixture.purge(jdbc, tenant));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFailingNotificationDoesNotUndoTheApproval() {
        String tenant = "tk-race-" + UUID.randomUUID().toString().substring(0, 8);
        Fixture race = transactions.execute(status -> Fixture.seed(jdbc, tenant, tenant.substring(8, 12)));
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox down")).when(notifications)
                .send(anyString(), anyString(), any());
        try {
            String id = transactions.execute(status -> requests.submit(tenant, race.teacher,
                    requests.create(tenant, race.teacher, new RequestCommand(RequestType.WFH, null, MON, MON, null,
                            null, null, null, "Ở nhà")).id()).id());
            transactions.executeWithoutResult(status -> approvals.approveStep(tenant, id, principalIn(tenant, race.lead),
                    false, null));

            assertThat(jdbc.queryForObject("select status from tk_requests where id = ?", String.class, id))
                    .isEqualTo("APPROVED");
            verify(notifications, times(1)).send(eq(tenant), anyString(), argThat((AdminSendRequest r) ->
                    r.idempotencyKey().equals("tk-req:" + id + ":APPROVED:1")));
        } finally {
            org.mockito.Mockito.reset(notifications);
            transactions.executeWithoutResult(status -> Fixture.purge(jdbc, tenant));
        }
    }

    private RequestView submit(RequestCommand command) {
        return requests.submit(TENANT, f.teacher, requests.create(TENANT, f.teacher, command).id());
    }

    private void approvedAnnual(LocalDate from, String days) {
        jdbc.update("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, leave_kind, from_date,"
                + " to_date, days, status) values (?, ?, ?, ?, ?, 'LEAVE', 'ANNUAL', ?, ?, ?::numeric, 'APPROVED')",
                UUID.randomUUID().toString(), TENANT, f.school, "DT-OLD-" + days, f.teacher, from, from, days);
    }

    private void missingOut(LocalDate date) {
        jdbc.update("insert into tk_days (id, tenant_id, school_id, user_id, work_date, status, work_units, missing)"
                + " values (?, ?, ?, ?, ?, 'MISSING_PUNCH', 0.5, 'OUT')", UUID.randomUUID().toString(), TENANT, f.school,
                f.teacher, date);
    }

    static RequestCommand leave(LeaveKind kind, LocalDate from, LocalDate to, DayPart part) {
        return new RequestCommand(RequestType.LEAVE, kind, from, to, part, null, null, null, "Việc gia đình");
    }

    private static RequestCommand explain(LocalDate date, LocalTime out) {
        return new RequestCommand(RequestType.EXPLANATION, null, null, null, null, date, out, ExplanationReason.FORGOT,
                "Quên chấm ra");
    }

    static ErgPrincipal principal(String userId, String... permissions) {
        return principalIn(TENANT, userId, permissions);
    }

    static ErgPrincipal principalIn(String tenant, String userId, String... permissions) {
        return new ErgPrincipal(userId, "session", tenant, List.of(), List.of(permissions), List.of(), List.of(),
                "STAFF", null, Map.of());
    }

    private static void assertError(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            if (message != null) assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    static boolean postgresAvailable() {
        String url = System.getProperty("erg.test.postgres.url", System.getenv("ERG_TEST_POSTGRES_URL"));
        if (url != null && !url.isBlank()) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** A school with a Mon–Fri shift, a teacher (manager = lead, joined 01/08/2016) and two vice principals. */
    public record Fixture(String school, String teacher, String lead, String vp, String vp2) {

        public static Fixture seed(JdbcTemplate jdbc, String tenant, String sfx) {
            Fixture f = new Fixture("sch_rq" + sfx, "rq_t" + sfx, "rq_l" + sfx, "rq_v" + sfx, "rq_w" + sfx);
            jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                    + " values (?, ?, 'Trường RQ', ?, 'school', 'active', now(), now())", f.school, tenant, f.school);
            for (String user : List.of(f.teacher, f.lead, f.vp, f.vp2)) {
                jdbc.update("insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)"
                        + " values (?, ?, ?, ?, 'active', now(), now())", user, tenant, user + "@example.test", "Tên " + user);
                jdbc.update("insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)"
                        + " values (?, ?, ?, 'lms.instructor', now(), now())", "scope_" + user, user, f.school);
            }
            jdbc.update("insert into employee_profiles (id, tenant_id, user_id, employee_code, joined_at, manager_id)"
                    + " values (?, ?, ?, ?, '2016-08-01', ?)", "ep_" + f.teacher, tenant, f.teacher, "E" + f.teacher, f.lead);
            for (String vp : List.of(f.vp, f.vp2)) {
                jdbc.update("insert into user_roles (user_id, role_id) values (?, 'role_school_vice_principal')", vp);
            }
            jdbc.update("insert into tk_shifts (id, tenant_id, school_id, name, start_time, end_time, work_units, weekdays,"
                    + " created_at, updated_at) values (?, ?, ?, 'Ca sáng', '07:30', '11:30', 1.0, '{1,2,3,4,5}', now(),"
                    + " now())", "shift_" + f.school, tenant, f.school);
            return f;
        }

        public static void purge(JdbcTemplate jdbc, String tenant) {
            for (String table : List.of("tk_audit_log", "tk_request_evidence", "tk_request_steps", "tk_requests", "tk_days",
                    "tk_code_counters", "tk_shifts", "employee_profiles")) {
                jdbc.update("delete from " + table + " where tenant_id = ?", tenant);
            }
            jdbc.update("delete from user_roles where user_id in (select id from users where tenant_id = ?)", tenant);
            jdbc.update("delete from user_access_scopes where user_id in (select id from users where tenant_id = ?)", tenant);
            jdbc.update("delete from users where tenant_id = ?", tenant);
            jdbc.update("delete from education_units where tenant_id = ?", tenant);
        }
    }
}
