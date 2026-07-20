package com.erg.ergspringboot.service.lms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class LmsClassWorkspaceQueryCountIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    static boolean postgresProofDatabaseAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired
    private LmsReadModelService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    void classWorkspaceReadModelKeepsQueryCountBoundedAcrossNestedRows() {
        seedClassWorkspace("tenant-qc", "class-qc", 12, 5);
        entityManager.flush();
        entityManager.clear();
        installTenantGuc("tenant-qc");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        ClassWorkspaceResponse response = service.classWorkspace(
                "tenant-qc",
                "class-qc",
                "school-qc",
                null,
                0,
                12,
                null,
                0,
                5,
                true,
                true
        );

        assertThat(response.students().items()).hasSize(12);
        assertThat(response.assignments().items()).hasSize(5);
        assertThat(response.scoreSummary()).isNotNull();
        assertThat(response.riskSummary()).isNotNull();

        assertThat(statistics.getPrepareStatementCount())
                .as("classWorkspace should use a bounded set of projection/page/aggregate SQL statements")
                .isLessThanOrEqualTo(8);
        assertThat(statistics.getEntityLoadCount())
                .as("projection read should not materialize entity graphs")
                .isZero();
    }

    private void seedClassWorkspace(String tenantId, String classId, int studentCount, int assignmentCount) {
        OffsetDateTime now = OffsetDateTime.now();
        EducationUnit school = new EducationUnit();
        school.setId("school-qc"); school.setTenantId(tenantId); school.setName("Query Count School"); school.setType("school");
        entityManager.persist(school);
        User teacher = new User();
        teacher.setId("teacher-qc"); teacher.setTenantId(tenantId); teacher.setUsername("teacher-qc");
        teacher.setEmail("teacher-qc@example.test"); teacher.setFullName("Query Count Teacher"); teacher.setStatus("ACTIVE");
        teacher.setMustChangePassword(false); teacher.setIsProfileCompleted(true); teacher.setRecoveryEmailVerified(false);
        entityManager.persist(teacher);
        Classroom classroom = new Classroom();
        classroom.setId(classId);
        classroom.setTenantId(tenantId);
        classroom.setSchoolId("school-qc");
        classroom.setName("Query Count 10A1");
        classroom.setGrade("10");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        classroom.setHomeroomTeacherId("teacher-qc");
        entityManager.persist(classroom);

        for (int index = 1; index <= studentCount; index++) {
            Student student = new Student();
            student.setId("student-qc-" + index);
            student.setTenantId(tenantId);
            student.setSchoolId("school-qc");
            student.setAcademicClassId(classId);
            student.setStudentCode("SQC" + index);
            student.setFullName("Student " + index);
            student.setUsername("student" + index);
            student.setEmail("student" + index + "@example.test");
            student.setStatus(index == studentCount ? "inactive" : "active");
            entityManager.persist(student);
        }

        for (int index = 1; index <= assignmentCount; index++) {
            Assignment assignment = new Assignment();
            assignment.setId("assignment-qc-" + index);
            assignment.setTenantId(tenantId);
            assignment.setAcademicClassId(classId);
            assignment.setQuizId("qz_math_g6_base");
            assignment.setSubjectId("sub_math");
            assignment.setDueAt(index <= 2 ? now.minusDays(index) : now.plusDays(index));
            assignment.setStatus("active");
            assignment.setAssignedBy("teacher-qc");
            assignment.setRecipientMode("CLASS");
            entityManager.persist(assignment);
        }

        for (int student = 1; student <= studentCount; student++) {
            for (int assignment = 1; assignment <= assignmentCount; assignment++) {
                if (assignment <= 2 && student % 3 == 0) {
                    continue;
                }
                Attempt attempt = new Attempt();
                attempt.setId("attempt-qc-" + student + "-" + assignment);
                attempt.setTenantId(tenantId);
                attempt.setAssignmentId("assignment-qc-" + assignment);
                attempt.setQuizId("qz_math_g6_base");
                attempt.setStudentId("student-qc-" + student);
                attempt.setStatus("submitted");
                attempt.setPercent(student % 4 == 0 ? 42.0D : 86.0D);
                attempt.setScore(student % 4 == 0 ? 42.0D : 86.0D);
                attempt.setMaxScore(100.0D);
                attempt.setPassed(student % 4 != 0);
                attempt.setStartedAt(now.minusHours(assignment + 1L));
                attempt.setSubmittedAt(now.minusHours(assignment));
                attempt.setUpdatedAt(now.minusHours(assignment));
                entityManager.persist(attempt);
            }
        }
    }

    private void installTenantGuc(String tenantId) {
        entityManager.createNativeQuery("select set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
