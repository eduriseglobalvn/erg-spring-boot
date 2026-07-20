package com.erg.ergspringboot.service.lms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.AssignmentResource;
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
class LmsAssignmentProgressWorkspaceQueryCountIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private LmsReadModelService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    static boolean postgresProofDatabaseAvailable() {
        if (firstText(
                System.getProperty("erg.test.postgres.url"),
                System.getenv("ERG_TEST_POSTGRES_URL")
        ) != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    @Test
    void assignmentProgressWorkspaceKeepsQueryCountBoundedAcrossAttempts() {
        seedAssignmentProgressWorkspace("tenant-apwq", "assignment-apwq", "teacher-apwq", 40);
        entityManager.flush();
        entityManager.clear();
        installTenantGuc("tenant-apwq");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        AssignmentProgressWorkspaceResponse response = service.assignmentProgressWorkspace(
                "tenant-apwq",
                "assignment-apwq",
                null,
                0,
                20
        );

        assertThat(response.assignment().id()).isEqualTo("assignment-apwq");
        assertThat(response.assignment().assignedBy()).isEqualTo("teacher-apwq");
        assertThat(response.assignment().resources()).hasSize(1);
        assertThat(response.assignment().resources().getFirst().quizVersionId()).isEqualTo("qv_math_g6_v1");
        assertThat(response.summary().submittedCount()).isEqualTo(53);
        assertThat(response.summary().inProgressCount()).isEqualTo(14);
        assertThat(response.summary().needsReviewCount()).isEqualTo(13);
        assertThat(response.attempts().items()).hasSize(20);
        assertThat(response.attempts().totalItems()).isEqualTo(80);
        assertThat(response.recipients().items()).hasSize(45);
        assertThat(response.recipients().totalItems()).isEqualTo(45);
        assertThat(response.recipients().items().getFirst().studentId()).isEqualTo("student-apwq-1");
        assertThat(response.recipients().items().getFirst().latestAttempt().id()).isEqualTo("attempt-apwq-latest-1");
        assertThat(response.recipients().items().getFirst().bestAttempt().id()).isEqualTo("attempt-apwq-best-1");
        assertThat(response.missingLateStudents().items()).hasSize(5);
        assertThat(response.missingLateStudents().totalItems()).isEqualTo(5);
        assertThat(response.missingLateStudents().items().getFirst().studentId()).isEqualTo("student-apwq-41");

        assertThat(statistics.getPrepareStatementCount())
                .as("assignmentProgressWorkspace should use bounded projection queries for detail, resources, summary, attempts, recipients, latest/best attempts and missing-late students")
                .isLessThanOrEqualTo(11);
        assertThat(statistics.getEntityLoadCount())
                .as("projection read should not materialize assignment/student/attempt entities or raw answers")
                .isZero();
    }

    private void seedAssignmentProgressWorkspace(
            String tenantId,
            String assignmentId,
            String teacherId,
            int attemptCount
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        EducationUnit school = new EducationUnit();
        school.setId("school-apwq"); school.setTenantId(tenantId); school.setName("Assignment Progress School"); school.setType("school");
        entityManager.persist(school);
        User teacher = new User();
        teacher.setId(teacherId); teacher.setTenantId(tenantId); teacher.setUsername(teacherId);
        teacher.setEmail(teacherId + "@example.test"); teacher.setFullName("Assignment Progress Teacher"); teacher.setStatus("ACTIVE");
        teacher.setMustChangePassword(false); teacher.setIsProfileCompleted(true); teacher.setRecoveryEmailVerified(false);
        entityManager.persist(teacher);
        OffsetDateTime dueAt = now.minusDays(1);
        Classroom classroom = new Classroom();
        classroom.setId("class-apwq");
        classroom.setTenantId(tenantId);
        classroom.setSchoolId("school-apwq");
        classroom.setName("Assignment Progress Query Count");
        classroom.setGrade("10");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        classroom.setHomeroomTeacherId(teacherId);
        entityManager.persist(classroom);

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTenantId(tenantId);
        assignment.setAcademicClassId(classroom.getId());
        assignment.setQuizId("qz_math_g6_base");
        assignment.setSubjectId("sub_math");
        assignment.setDueAt(dueAt);
        assignment.setStatus("active");
        assignment.setAssignedBy(teacherId);
        assignment.setRecipientMode("CLASS");
        entityManager.persist(assignment);

        AssignmentResource resource = new AssignmentResource();
        resource.setAssignmentId(assignmentId);
        resource.setQuizId(assignment.getQuizId());
        resource.setTenantId(tenantId);
        resource.setQuizVersionId("qv_math_g6_v1");
        resource.setQuizVersionLabel("v1");
        resource.setOrderIndex(0);
        resource.setCreatedAt(now);
        entityManager.persist(resource);

        int studentCount = attemptCount + 5;
        for (int index = 1; index <= studentCount; index++) {
            Student student = new Student();
            student.setId("student-apwq-" + index);
            student.setTenantId(tenantId);
            student.setSchoolId(classroom.getSchoolId());
            student.setAcademicClassId(classroom.getId());
            student.setStudentCode(String.format("S%03d", index));
            student.setFullName(String.format("Student %03d", index));
            student.setUsername("student" + index);
            student.setEmail("student" + index + "@example.com");
            student.setStatus("active");
            entityManager.persist(student);
        }

        for (int index = 1; index <= attemptCount; index++) {
            Attempt bestAttempt = new Attempt();
            bestAttempt.setId("attempt-apwq-best-" + index);
            bestAttempt.setTenantId(tenantId);
            bestAttempt.setAssignmentId(assignmentId);
            bestAttempt.setQuizId(assignment.getQuizId());
            bestAttempt.setStudentId("student-apwq-" + index);
            bestAttempt.setStatus("submitted");
            bestAttempt.setScore(80.0D + (index % 10));
            bestAttempt.setMaxScore(100.0D);
            bestAttempt.setPercent(80.0D + (index % 10));
            bestAttempt.setPassed(true);
            bestAttempt.setStartedAt(dueAt.minusHours(index + 2L));
            bestAttempt.setSubmittedAt(dueAt.minusHours(1));
            bestAttempt.setUpdatedAt(dueAt.minusMinutes(index));
            entityManager.persist(bestAttempt);

            Attempt latestAttempt = new Attempt();
            latestAttempt.setId("attempt-apwq-latest-" + index);
            latestAttempt.setTenantId(tenantId);
            latestAttempt.setAssignmentId(assignmentId);
            latestAttempt.setQuizId(assignment.getQuizId());
            latestAttempt.setStudentId("student-apwq-" + index);
            if (index % 3 == 0) {
                latestAttempt.setStatus("submitted");
            } else if (index % 3 == 1) {
                latestAttempt.setStatus("in_progress");
            } else {
                latestAttempt.setStatus("needs_review");
            }
            latestAttempt.setScore(60.0D + (index % 10));
            latestAttempt.setMaxScore(100.0D);
            latestAttempt.setPercent(60.0D + (index % 10));
            latestAttempt.setPassed(index % 2 == 0);
            latestAttempt.setStartedAt(now.minusHours(index + 2L));
            latestAttempt.setSubmittedAt(index % 3 == 0 ? now.minusHours(index) : null);
            latestAttempt.setUpdatedAt(now.minusMinutes(index));
            entityManager.persist(latestAttempt);
        }
    }

    private void installTenantGuc(String tenantId) {
        entityManager.createNativeQuery("select set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
