package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.ErgSpringApplication;
import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.testcontainers.DockerClientFactory;

/**
 * One shared Spring context (PostgreSQL, MockMvc, clock fixed at Thu 12/11/2026 08:00 Vietnam) for the admin
 * service tests and the admin/approval controller IT, plus small row builders.
 */
@ActiveProfiles("test")
@SpringBootTest(classes = {ErgSpringApplication.class, AdminITBase.FixedClock.class})
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.erg.ergspringboot.timekeeping.internal.admin.AdminITBase#postgresAvailable")
public abstract class AdminITBase {

    public static final Instant NOW = Instant.parse("2026-11-12T01:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @TestConfiguration
    public static class FixedClock {
        @Bean
        @Primary
        Clock fixedTimekeepingClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @MockitoBean protected CalendarReadPort calendar;
    @MockitoBean protected NotificationAdminOperations notifications;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TransactionTemplate transactions;
    @PersistenceContext protected EntityManager entityManager;

    /** Pushes pending JPA writes so plain JDBC assertions see them. */
    protected void flush() {
        entityManager.flush();
    }

    public static boolean postgresAvailable() {
        String url = System.getProperty("erg.test.postgres.url", System.getenv("ERG_TEST_POSTGRES_URL"));
        if (url != null && !url.isBlank()) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static ErgPrincipal principal(String tenant, String userId, String... permissions) {
        return new ErgPrincipal(userId, "session", tenant, List.of(), List.of(permissions), List.of(), List.of(),
                "STAFF", null, Map.of());
    }

    /** An ACTIVE teacher (holds timekeeping.self through role_lms_teacher) scoped to {@code school}. */
    protected void teacher(String tenant, String id, String school, String name) {
        jdbc.update("insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)"
                + " values (?, ?, ?, ?, 'active', now(), now())", id, tenant, id + "@example.test", name);
        jdbc.update("insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)"
                + " values (?, ?, ?, 'lms.instructor', now(), now())", "scope_" + id, id, school);
        jdbc.update("insert into user_roles (user_id, role_id) values (?, 'role_lms_teacher')", id);
    }

    protected String punch(String tenant, String school, String user, String direction, Instant at, String status) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into tk_punches (id, tenant_id, school_id, code, user_id, work_date, direction, server_time,"
                + " status, idempotency_key) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, tenant, school,
                "CC-" + id.substring(0, 12), user, java.sql.Date.valueOf(at.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toLocalDate()), direction, java.sql.Timestamp.from(at), status, id);
        return id;
    }

    protected void day(String tenant, String school, String user, LocalDate date, String status, String units,
            int lateMinutes, String leaveKind) {
        jdbc.update("insert into tk_days (id, tenant_id, school_id, user_id, work_date, status, work_units, late_minutes,"
                + " deduct_minutes, leave_kind) values (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?, ?)",
                UUID.randomUUID().toString(), tenant, school, user, java.sql.Date.valueOf(date), status, units,
                lateMinutes, lateMinutes == 0 ? 0 : 15, leaveKind);
    }

    /** A PENDING (or other status) request row written directly (no chain), e.g. to block a period lock. */
    protected String request(String tenant, String school, String user, String type, String leaveKind, LocalDate from,
            LocalDate to, String status) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, leave_kind, from_date,"
                + " to_date, part, days, status, current_step, submitted_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'FULL',"
                + " ?, ?, 1, now())", id, tenant, school, "DT-T-" + id.substring(0, 8), user, type, leaveKind,
                java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), (int) (to.toEpochDay() - from.toEpochDay() + 1),
                status);
        return id;
    }
}
