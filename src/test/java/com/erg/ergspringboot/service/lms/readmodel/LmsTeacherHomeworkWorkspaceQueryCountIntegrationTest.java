package com.erg.ergspringboot.service.lms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
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
class LmsTeacherHomeworkWorkspaceQueryCountIntegrationTest {

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
    void teacherHomeworkWorkspaceKeepsQueryCountBoundedAcrossAssignmentsAndAttempts() {
        seedTeacherHomeworkWorkspace("tenant-thwq", "teacher-thwq", 4, 16, 3);
        entityManager.flush();
        entityManager.clear();
        installTenantGuc("tenant-thwq");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        TeacherHomeworkWorkspaceResponse response = service.teacherHomeworkWorkspace(
                "tenant-thwq",
                "teacher-thwq",
                null,
                0,
                10
        );

        assertThat(response.teacherId()).isEqualTo("teacher-thwq");
        assertThat(response.assignments().items()).hasSize(10);
        assertThat(response.assignments().totalItems()).isEqualTo(16);
        assertThat(response.summary().activeAssignmentCount()).isEqualTo(12);
        assertThat(response.summary().overdueAssignmentCount()).isEqualTo(3);
        assertThat(response.summary().pendingReviewAssignmentCount()).isEqualTo(8);
        assertThat(response.classOptions()).hasSize(4);
        assertThat(response.pendingReviews().items()).hasSize(10);
        assertThat(response.pendingReviews().totalItems()).isEqualTo(24);
        assertThat(response.overdueStudents().items()).isNotEmpty();
        assertThat(response.overdueStudents().items())
                .allSatisfy(item -> assertThat(item.status()).isEqualTo("active"));

        assertThat(statistics.getPrepareStatementCount())
                .as("teacherHomeworkWorkspace uses nine fixed projection/page statements; the count must not grow with row cardinality")
                .isLessThanOrEqualTo(9);
        assertThat(statistics.getEntityLoadCount())
                .as("projection read should not materialize assignment/attempt/class graphs")
                .isZero();
    }

    private void seedTeacherHomeworkWorkspace(
            String tenantId,
            String teacherId,
            int classCount,
            int assignmentCount,
            int attemptsPerSubmittedAssignment
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        EducationUnit school = new EducationUnit();
        school.setId("school-thwq"); school.setTenantId(tenantId); school.setName("Teacher Homework School"); school.setType("school");
        entityManager.persist(school);
        User teacher = new User();
        teacher.setId(teacherId); teacher.setTenantId(tenantId); teacher.setUsername(teacherId);
        teacher.setEmail(teacherId + "@example.test"); teacher.setFullName("Teacher Homework"); teacher.setStatus("ACTIVE");
        teacher.setMustChangePassword(false); teacher.setIsProfileCompleted(true); teacher.setRecoveryEmailVerified(false);
        entityManager.persist(teacher);
        for (int index = 1; index <= classCount; index++) {
            Classroom classroom = new Classroom();
            classroom.setId("class-thwq-" + index);
            classroom.setTenantId(tenantId);
            classroom.setSchoolId("school-thwq");
            classroom.setName("Teacher Homework " + index);
            classroom.setGrade("10");
            classroom.setAcademicYear("2026");
            classroom.setStatus("active");
            classroom.setHomeroomTeacherId(teacherId);
            entityManager.persist(classroom);

            for (int studentIndex = 1; studentIndex <= 5; studentIndex++) {
                Student student = new Student();
                student.setId("student-thwq-" + index + "-" + studentIndex);
                student.setTenantId(tenantId);
                student.setSchoolId("school-thwq");
                student.setAcademicClassId(classroom.getId());
                student.setStudentCode("S-THWQ-" + index + "-" + studentIndex);
                student.setFullName("Teacher Homework Student " + index + "-" + studentIndex);
                student.setUsername("thwq" + index + studentIndex);
                student.setEmail("thwq" + index + studentIndex + "@example.com");
                student.setStatus("active");
                entityManager.persist(student);
            }
        }

        for (int index = 1; index <= assignmentCount; index++) {
            Assignment assignment = new Assignment();
            assignment.setId("assignment-thwq-" + index);
            assignment.setTenantId(tenantId);
            assignment.setAcademicClassId("class-thwq-" + (((index - 1) % classCount) + 1));
            assignment.setQuizId("qz_math_g6_base");
            assignment.setSubjectId("sub_math");
            assignment.setDueAt(index <= 4 ? now.minusDays(index) : now.plusDays(index));
            assignment.setStatus(index % 4 == 0 ? "draft" : "active");
            assignment.setAssignedBy(teacherId);
            assignment.setRecipientMode("CLASS");
            entityManager.persist(assignment);

            if (index % 2 == 0) {
                for (int attemptIndex = 1; attemptIndex <= attemptsPerSubmittedAssignment; attemptIndex++) {
                    int classIndex = ((index - 1) % classCount) + 1;
                    Attempt attempt = new Attempt();
                    attempt.setId("attempt-thwq-" + index + "-" + attemptIndex);
                    attempt.setTenantId(tenantId);
                    attempt.setAssignmentId(assignment.getId());
                    attempt.setQuizId(assignment.getQuizId());
                    attempt.setStudentId("student-thwq-" + classIndex + "-" + attemptIndex);
                    attempt.setStatus("submitted");
                    attempt.setPercent(80.0D + attemptIndex);
                    attempt.setScore(80.0D + attemptIndex);
                    attempt.setMaxScore(100.0D);
                    attempt.setPassed(true);
                    attempt.setStartedAt(now.minusHours(index + attemptIndex + 1L));
                    attempt.setSubmittedAt(now.minusHours(index + attemptIndex));
                    attempt.setUpdatedAt(now.minusHours(index + attemptIndex));
                    entityManager.persist(attempt);
                }
            }
        }
    }

    private void installTenantGuc(String tenantId) {
        entityManager.createNativeQuery("select set_config('app.tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
