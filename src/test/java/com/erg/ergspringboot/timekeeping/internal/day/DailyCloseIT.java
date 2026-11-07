package com.erg.ergspringboot.timekeeping.internal.day;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.timekeeping.internal.request.LeaveBalanceService;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
import com.erg.ergspringboot.users.persistence.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** Daily close, day aggregation, roster and leave balance on real PostgreSQL. Clock: Fri 13/11/2026 00:30 VN. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresAvailable")
class DailyCloseIT {

    private static final Instant NOW = Instant.parse("2026-11-12T17:30:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final String TENANT = "tk-close";
    private static final String SCHOOL = "sch_tk_close";

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
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyCloseJob job;
    @Autowired DayAggregationService aggregation;
    @Autowired LeaveBalanceService leaveBalance;
    @Autowired UserRepository users;
    @Autowired TeacherPeriodReader periodReader;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    @BeforeEach
    void seed() {
        jdbc.update("insert into education_units (id, tenant_id, name, slug, type, status, created_at, updated_at)"
                + " values (?, ?, 'Trường chốt', 'truong-chot', 'school', 'active', now(), now())", SCHOOL, TENANT);
        jdbc.update("insert into tk_shifts (id, tenant_id, school_id, name, start_time, end_time, work_units, weekdays,"
                + " created_at, updated_at) values ('shift-tk-close', ?, ?, 'Ca sáng', '07:30', '11:30', 1.0,"
                + " '{1,2,3,4,5}', '2020-01-01', now())", TENANT, SCHOOL);
        staff("usr_tkc_a", "active", true);   // IN only
        staff("usr_tkc_b", "ACTIVE", false);  // role through a scoped assignment, nothing that day
        jdbc.update("insert into user_role_assignments (id, user_id, role_id, tenant_id, scope_type, assigned_by)"
                + " values ('ura_tkc_b', 'usr_tkc_b', 'role_lms_teacher', ?, 'tenant', 'usr_tkc_a')", TENANT);
        staff("usr_tkc_c", "active", true);   // approved UNPAID leave
        staff("usr_tkc_d", "active", true);   // deny override: not on the roster
        jdbc.update("insert into user_permissions (id, user_id, permission, grant_type) values"
                + " ('up_tkc_d', 'usr_tkc_d', 'timekeeping.self', 'deny')");
        staff("usr_tkc_e", "inactive", true); // not active: not on the roster
        staff("usr_tkc_f", "active", true);   // PENDING leave only
        staff("usr_tkc_g", "active", true);   // approved AM leave
        punch("usr_tkc_a", "IN", "2026-11-12T00:25:00Z");
        request("rq_c", "usr_tkc_c", "LEAVE", "UNPAID", "FULL", "APPROVED", "2026-11-12", "2026-11-12", "1.0");
        request("rq_f", "usr_tkc_f", "LEAVE", "ANNUAL", "FULL", "PENDING", "2026-11-12", "2026-11-12", "1.0");
        request("rq_g", "usr_tkc_g", "LEAVE", "ANNUAL", "AM", "APPROVED", "2026-11-12", "2026-11-12", "0.5");
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name) values"
                + " ('hol-other', ?, 'sch_other', ?, 'Lễ trường khác')", TENANT, DAY);
    }

    @Test
    void closeBuildsEveryRosterDayAndIsIdempotent() {
        job.run(); // other tenants in the shared database may add to the returned count
        Map<String, Map<String, Object>> first = rows();

        assertThat(first).containsOnlyKeys("usr_tkc_a", "usr_tkc_b", "usr_tkc_c", "usr_tkc_f", "usr_tkc_g");
        assertThat(first.get("usr_tkc_a")).containsEntry("status", "MISSING_PUNCH").containsEntry("missing", "OUT");
        assertThat(first.get("usr_tkc_a").get("work_units").toString()).isEqualTo("0.5");
        assertThat(first.get("usr_tkc_b")).containsEntry("status", "ABSENT_UNPAID");
        assertThat(first.get("usr_tkc_c")).containsEntry("status", "LEAVE").containsEntry("leave_kind", "UNPAID");
        assertThat(first.get("usr_tkc_f")).containsEntry("status", "ABSENT_UNPAID");
        assertThat(first.get("usr_tkc_g")).containsEntry("status", "HALF").containsEntry("leave_kind", "ANNUAL");
        assertThat(first.get("usr_tkc_g").get("work_units").toString()).isEqualTo("0.5");

        entityManager.flush();
        entityManager.clear();
        job.run(); // other tenants in the shared database may add to the returned count
        assertThat(rows()).isEqualTo(first);
    }

    @Test
    void approvedExplanationRecomputesTheMissingOutToFull() {
        job.run();
        request("rq_a", "usr_tkc_a", "EXPLANATION", null, "FULL", "APPROVED", null, null, null);
        jdbc.update("update tk_requests set work_date = ?, proposed_out = '11:30' where id = 'rq_a'", DAY);

        aggregation.recompute(TENANT, "usr_tkc_a", DAY);
        entityManager.flush();

        Map<String, Object> row = rows().get("usr_tkc_a");
        assertThat(row).containsEntry("status", "FULL");
        assertThat(row.get("missing")).isNull();
        assertThat(row.get("work_units").toString()).isEqualTo("1.0");
    }

    @Test
    void tenantWideHolidayWinsAndALockedMonthIsLeftAlone() {
        jdbc.update("insert into tk_holidays (id, tenant_id, school_id, holiday_date, name) values"
                + " ('hol-all', ?, null, ?, 'Lễ')", TENANT, DAY);
        aggregation.recompute(TENANT, "usr_tkc_a", DAY);
        entityManager.flush();
        assertThat(rows().get("usr_tkc_a")).containsEntry("status", "HOLIDAY");

        jdbc.update("insert into tk_period_locks (id, tenant_id, school_id, period, locked_at, locked_by)"
                + " values ('lock-tkc', ?, ?, '2026-11', now(), 'usr_tkc_a')", TENANT, SCHOOL);
        job.run();
        assertThat(rows()).containsOnlyKeys("usr_tkc_a");
    }

    @Test
    void leaveBalanceCountsSeniorityAndOnlyApprovedAnnualLeave() {
        jdbc.update("insert into employee_profiles (id, tenant_id, user_id, employee_code, joined_at) values"
                + " ('ep_tkc_a', ?, 'usr_tkc_a', 'NV-001', '2016-08-01')", TENANT);
        request("rq_a1", "usr_tkc_a", "LEAVE", "ANNUAL", "FULL", "APPROVED", "2026-03-02", "2026-03-06", "5.0");
        request("rq_a2", "usr_tkc_a", "LEAVE", "ANNUAL", "AM", "APPROVED", "2026-04-07", "2026-04-07", "0.5");
        request("rq_a3", "usr_tkc_a", "LEAVE", "ANNUAL", "FULL", "CANCELLED", "2026-05-04", "2026-05-05", "2.0");
        request("rq_a4", "usr_tkc_a", "LEAVE", "SICK", "FULL", "APPROVED", "2026-06-01", "2026-06-01", "1.0");

        assertThat(leaveBalance.annualBalance(TENANT, "usr_tkc_a", 2026)).isEqualByComparingTo("8.5");
        assertThat(leaveBalance.annualBalance(TENANT, "usr_tkc_b", 2026)).isEqualByComparingTo("12");
    }

    @Test
    void rosterLeavesOutStaffWhoLeftBeforeOrJoinAfterTheDay() {
        staff("usr_tkc_t", "active", true);
        staff("usr_tkc_j", "active", true);
        staff("usr_tkc_x", "active", true);
        profile("usr_tkc_a", "2016-08-01", null);
        profile("usr_tkc_t", "2020-01-01", "2026-11-11"); // left the day before
        profile("usr_tkc_j", "2026-11-13", null);         // joins the day after
        profile("usr_tkc_x", "2020-01-01", "2026-11-12"); // last day is the day itself

        List<String> roster = users.findActiveUserIdsHoldingPermission(TENANT, "timekeeping.self", DAY);

        assertThat(roster).contains("usr_tkc_a", "usr_tkc_b", "usr_tkc_x") // b has no profile row
                .doesNotContain("usr_tkc_t", "usr_tkc_j", "usr_tkc_d", "usr_tkc_e");
    }

    @Test
    void failingCalendarReadDoesNotMarkTheCallersTransactionRollbackOnly() {
        // Behaves like the real @Transactional calendar use case: joins a running transaction, then fails.
        when(calendar.teacherPeriods(any(), any(), any(), any())).thenAnswer(call -> new TransactionTemplate(
                transactionManager).execute(status -> { throw new IllegalStateException("calendar down"); }));
        TransactionTemplate punchLike = new TransactionTemplate(transactionManager);
        punchLike.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        boolean[] rollbackOnly = new boolean[1];
        punchLike.executeWithoutResult(status -> {
            assertThat(periodReader.read(TENANT, "usr_tkc_a", DAY, DAY)).isEmpty();
            rollbackOnly[0] = status.isRollbackOnly();
        });
        assertThat(rollbackOnly[0]).isFalse();

        // Without the reader the same failure, even caught, dooms the caller's transaction.
        assertThatThrownBy(() -> punchLike.executeWithoutResult(status -> {
            try {
                calendar.teacherPeriods(TENANT, "usr_tkc_a", DAY, DAY);
            } catch (IllegalStateException caught) {
                // swallowed, as a plain try/catch would
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
    }

    private void profile(String userId, String joinedAt, String terminatedAt) {
        jdbc.update("insert into employee_profiles (id, tenant_id, user_id, employee_code, joined_at, terminated_at)"
                + " values (?, ?, ?, ?, ?::date, ?::date)", "ep_" + userId, TENANT, userId, "NV-" + userId,
                joinedAt, terminatedAt);
    }

    private Map<String, Map<String, Object>> rows() {
        entityManager.flush(); // JPA writes are pending in the test transaction
        List<Map<String, Object>> list = jdbc.queryForList("select user_id, status, work_units, missing, leave_kind,"
                + " late_minutes, deduct_minutes, periods, school_id, shift_id, in_punch_id, id from tk_days"
                + " where tenant_id = ? and work_date = ?", TENANT, DAY);
        return list.stream().collect(Collectors.toMap(r -> (String) r.get("user_id"), r -> r));
    }

    private void staff(String id, String status, boolean directRole) {
        jdbc.update("insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, now(), now())", id, TENANT, id + "@example.test", id, status);
        jdbc.update("insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)"
                + " values (?, ?, ?, 'lms.instructor', now(), now())", "sc_" + id.substring(4), id, SCHOOL);
        if (directRole) {
            jdbc.update("insert into user_roles (user_id, role_id) values (?, 'role_lms_teacher')", id);
        }
    }

    private void punch(String userId, String direction, String at) {
        jdbc.update("insert into tk_punches (id, tenant_id, school_id, code, user_id, shift_id, work_date, direction,"
                + " server_time, status, method, idempotency_key) values (?, ?, ?, ?, ?, 'shift-tk-close', ?, ?, ?,"
                + " 'VALID', 'GPS', ?)", "p-" + userId, TENANT, SCHOOL, "CC-121126-" + userId.substring(8), userId,
                DAY, direction, java.sql.Timestamp.from(Instant.parse(at)), "k-" + userId);
    }

    private void request(String id, String userId, String type, String kind, String part, String status, String from,
            String to, String days) {
        jdbc.update("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, leave_kind, part, status,"
                + " from_date, to_date, days) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?::numeric)",
                id, TENANT, SCHOOL, "DT-" + id, userId, type, kind, part, status, from, to, days);
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
}
