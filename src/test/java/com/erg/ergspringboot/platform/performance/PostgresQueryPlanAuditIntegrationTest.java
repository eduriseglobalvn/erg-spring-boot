package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * PostgreSQL-only plan regression for representative tenant-scoped read paths.
 * This guards index selection and query shape; it is not a production latency benchmark.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class PostgresQueryPlanAuditIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    static boolean postgresProofDatabaseAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Test
    void representativeTenantScopedReadPlansUseExpectedIndexes() {
        seedPlanData();
        entityManager.createNativeQuery("analyze lms_assignments").executeUpdate();
        entityManager.createNativeQuery("analyze lms_attempts").executeUpdate();
        entityManager.createNativeQuery("analyze teaching_schedule_events").executeUpdate();

        String assignmentPlan = explain("""
                select id, academic_class_id, status, due_at, created_at
                from lms_assignments
                where tenant_id = 'erg'
                  and academic_class_id = 'cls_ba_2025_6a1'
                  and status = 'active'
                order by due_at asc nulls last, created_at desc
                limit 50
                """);
        String attemptPlan = explain("""
                select id, assignment_id, status, updated_at
                from lms_attempts
                where tenant_id = 'erg'
                  and student_id = 'stu_ba_0001'
                order by updated_at desc
                limit 50
                """);
        String calendarPlan = explain("""
                select id, academic_class_id, start_at, end_at, status
                from teaching_schedule_events
                where tenant_id = 'erg'
                  and school_id = 'sch_binh_an'
                  and start_at < timestamp with time zone '2026-08-01T00:00:00Z'
                  and end_at > timestamp with time zone '2026-07-01T00:00:00Z'
                order by start_at asc
                limit 100
                """);

        assertPlan(assignmentPlan, "idx_lms_assignments_read_class_status_due", "lms_assignments", "tenant_id");
        assertPlan(attemptPlan, "idx_lms_attempts_read_student_updated", "lms_attempts", "tenant_id");
        assertPlan(calendarPlan, "teaching_schedule_events", "tenant_id",
                "idx_tse_tenant_school_timerange", "idx_tse_tenant_timerange");
    }

    private void seedPlanData() {
        entityManager.createNativeQuery("""
                insert into lms_assignments
                    (id, created_at, updated_at, tenant_id, academic_class_id, quiz_id, subject_id, due_at, status, assigned_by, recipient_mode)
                select 'pa-' || to_char(n, 'FM000000'), now(), now(),
                       case when n % 2 = 0 then 'erg' else 'tenant-plan-other' end,
                       'cls_ba_2025_6a1',
                       'qz_math_g6_base', 'sub_math', now() + (n || ' minutes')::interval,
                       case when n % 3 = 0 then 'draft' else 'active' end, 'usr_teacher_ba', 'CLASS'
                from generate_series(1, 4000) n
                """).executeUpdate();
        entityManager.createNativeQuery("""
                insert into lms_attempts
                    (id, tenant_id, assignment_id, quiz_id, student_id, status, score, max_score, percent, passed, started_at, updated_at)
                select 'pt-' || to_char(n, 'FM000000'),
                       case when n % 2 = 0 then 'erg' else 'tenant-plan-other' end,
                       'pa-' || to_char(case when n % 2 = 0 then n else n + 1 end, 'FM000000'),
                       'qz_math_g6_base',
                       case when n % 4 = 0 then 'stu_ba_0001' else 'stu_cl_0001' end,
                       'submitted', 80, 100, 80, true, now() - (n || ' minutes')::interval, now() - (n || ' seconds')::interval
                from generate_series(1, 4000) n
                """).executeUpdate();
        entityManager.createNativeQuery("""
                insert into teaching_schedule_events
                    (id, tenant_id, batch_id, school_id, academic_class_id, subject_id, title,
                     start_at, end_at, weekday, session, period_start, period_count, status, created_at, updated_at)
                select 'pe-' || to_char(n, 'FM000000'),
                       case when n % 2 = 0 then 'erg' else 'tenant-plan-other' end,
                       'batch_ba_demo_001',
                       'sch_binh_an', 'cls_ba_2025_6a1', 'sub_math', 'Plan audit',
                       timestamp with time zone '2026-07-01T00:00:00Z' + (n || ' minutes')::interval,
                       timestamp with time zone '2026-07-01T00:30:00Z' + (n || ' minutes')::interval,
                       ((n - 1) % 7) + 1, 'MORNING', 1, 1, 'PUBLISHED', now(), now()
                from generate_series(1, 4000) n
                """).executeUpdate();
    }

    private String explain(String sql) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement("explain (analyze, buffers, format text) " + sql);
                 var result = statement.executeQuery()) {
                var lines = new java.util.ArrayList<String>();
                while (result.next()) lines.add(result.getString(1));
                return String.join("\n", lines);
            }
        });
    }

    private void assertPlan(String plan, String table, String tenantColumn, String... expectedIndexes) {
        assertThat(plan).contains(table).contains(tenantColumn).contains("Execution Time");
        assertThat(java.util.Arrays.stream(expectedIndexes).anyMatch(plan::contains))
                .as("plan should use one of the tenant-leading indexes %s", java.util.Arrays.toString(expectedIndexes))
                .isTrue();
    }
}
