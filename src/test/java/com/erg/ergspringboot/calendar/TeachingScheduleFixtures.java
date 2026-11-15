package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.ErgSpringApplication;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

/**
 * Real PostgreSQL + full Spring context for teaching-schedule tests. Not transactional (concurrency tests need real
 * commits); every test uses its own random tenant so rows never collide. Clock is fixed at Thu 12/11/2026 08:00
 * Vietnam time.
 */
@ActiveProfiles("test")
@SpringBootTest(classes = {ErgSpringApplication.class, TeachingScheduleFixtures.FixedClock.class})
@EnabledIf("com.erg.ergspringboot.calendar.TeachingScheduleFixtures#postgresAvailable")
public abstract class TeachingScheduleFixtures {

    public static final Instant NOW = Instant.parse("2026-11-12T01:00:00Z");
    protected static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @TestConfiguration
    public static class FixedClock {
        @Bean
        @Primary
        Clock fixedTeachingScheduleClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TransactionTemplate transactions;

    /** Hikari runs with auto-commit off, so every fixture write commits in its own transaction. */
    protected int write(String sql, Object... args) {
        return transactions.execute(status -> jdbc.update(sql, args));
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

    protected static String id() {
        return UUID.randomUUID().toString();
    }

    protected String school(String tenant, String name) {
        String id = id();
        write("insert into education_units (id, tenant_id, name, created_at, updated_at) values (?, ?, ?, now(), now())",
                id, tenant, name);
        return id;
    }

    protected String classroom(String tenant, String school, String name) {
        String id = id();
        write("insert into academic_classes (id, tenant_id, school_id, name, created_at, updated_at)"
                + " values (?, ?, ?, ?, now(), now())", id, tenant, school, name);
        return id;
    }

    protected String subject(String tenant, String name) {
        String id = id();
        write("insert into lms_subjects (id, tenant_id, name, created_at, updated_at) values (?, ?, ?, now(), now())",
                id, tenant, name);
        return id;
    }

    protected String user(String tenant, String fullName) {
        String id = id();
        write("insert into users (id, tenant_id, email, full_name, status, created_at, updated_at)"
                + " values (?, ?, ?, ?, 'active', now(), now())", id, tenant, id + "@example.test", fullName);
        return id;
    }

    /** A teacher the calendar catalog can see for {@code school} (lms.instructor scope), like the admin ITs use. */
    protected String instructor(String tenant, String fullName, String school) {
        String id = user(tenant, fullName);
        write("insert into user_access_scopes (id, user_id, education_unit_id, role, created_at, updated_at)"
                + " values (?, ?, ?, 'lms.instructor', now(), now())", id(), id, school);
        return id;
    }

    /** Instant for a Vietnam wall-clock date and time. */
    protected static OffsetDateTime at(String date, String time) {
        return java.time.LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(VN).toOffsetDateTime();
    }

    protected String room(String tenant, String school, String name) {
        String id = id();
        write("insert into teaching_schedule_rooms (id, tenant_id, school_id, name, created_at, updated_at)"
                + " values (?, ?, ?, ?, now(), now())", id, tenant, school, name);
        return id;
    }

    protected String batch(String tenant, String school, String status, String label) {
        String id = id();
        write("insert into teaching_schedule_batches (id, tenant_id, school_id, apply_from, repeat_weeks, status, label)"
                + " values (?, ?, ?, date '2026-11-16', 10, ?, ?)", id, tenant, school, status, label);
        return id;
    }

    /** One-period event; the period lasts 45 minutes from {@code start}. */
    protected String event(String tenant, String batch, String school, String classId, String subject, String status,
            OffsetDateTime start, int periodNo, String teacherId, String assistantId, String roomId) {
        String id = id();
        OffsetDateTime end = start.plusMinutes(45);
        write("insert into teaching_schedule_events (id, tenant_id, batch_id, school_id, academic_class_id, subject_id,"
                + " room_id, room_name, start_at, end_at, weekday, period_start, period_count, status)"
                + " values (?, ?, ?, ?, ?, ?, ?, (select name from teaching_schedule_rooms where id = ?), ?, ?, ?, ?, 1, ?)",
                id, tenant, batch, school, classId, subject, roomId, roomId, Timestamp.from(start.toInstant()),
                Timestamp.from(end.toInstant()), start.atZoneSameInstant(VN).getDayOfWeek().getValue(), periodNo, status);
        if (teacherId != null) link(tenant, id, teacherId, "MAIN");
        if (assistantId != null) link(tenant, id, assistantId, "ASSISTANT");
        return id;
    }

    protected void link(String tenant, String eventId, String userId, String role) {
        write("insert into teaching_schedule_event_teachers (id, tenant_id, event_id, teacher_user_id, role)"
                + " values (?, ?, ?, ?, ?)", id(), tenant, eventId, userId, role);
    }
}
