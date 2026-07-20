package com.erg.ergspringboot.service.elearning.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class StudentDashboardQueryCountIntegrationTest {

    @Autowired
    private StudentDashboardReadService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    void studentDashboardReadModelKeepsQueryCountBoundedAcrossAssignmentsAndAttempts() {
        seedStudentDashboard("tenant-sdq", "student-user-sdq", "student-row-sdq", "class-sdq", 12);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        StudentDashboardResponse response = service.studentDashboard(new StudentDashboardReadScope(
                "student-user-sdq",
                "tenant-sdq",
                null,
                null,
                List.of("student"),
                List.of("elearning.assignment.read_self"),
                List.of(),
                List.of("elearning"),
                "student",
                "self"
        ));

        assertThat(response.profile().studentId()).isEqualTo("student-row-sdq");
        assertThat(response.assignments()).hasSize(8);
        assertThat(response.nextActions()).hasSizeLessThanOrEqualTo(5);
        assertThat(response.summary().activeAssignmentCount()).isEqualTo(12);
        assertThat(response.summary().scoreSummary()).isNotNull();
        assertThat(response.announcements())
                .as("Announcement persistence is not wired yet; dashboard must not invent or leak target data.")
                .isEmpty();

        assertThat(statistics.getPrepareStatementCount())
                .as("studentDashboard should use a bounded set of profile, projection and aggregate SQL statements")
                .isLessThanOrEqualTo(6);
        assertThat(statistics.getEntityLoadCount())
                .as("studentDashboard may load only the profile User and Student entities, not assignment/attempt graphs")
                .isLessThanOrEqualTo(2);
    }

    private void seedStudentDashboard(
            String tenantId,
            String userId,
            String studentId,
            String classId,
            int assignmentCount
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        EducationUnit school = new EducationUnit();
        school.setId("school-sdq"); school.setTenantId(tenantId); school.setName("Student Dashboard School"); school.setType("school");
        entityManager.persist(school);

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("student-sdq@example.test");
        user.setFullName("Student Dashboard Query Count");
        user.setStatus("active");
        user.setAccountType("student");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        entityManager.persist(user);

        Classroom classroom = new Classroom();
        classroom.setId(classId);
        classroom.setTenantId(tenantId);
        classroom.setSchoolId("school-sdq");
        classroom.setName("Student Dashboard 10A1");
        classroom.setGrade("10");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        classroom.setHomeroomTeacherId(userId);
        entityManager.persist(classroom);

        Student student = new Student();
        student.setId(studentId);
        student.setTenantId(tenantId);
        student.setSchoolId("school-sdq");
        student.setAcademicClassId(classId);
        student.setStudentCode("SDQ001");
        student.setFullName("Student Dashboard Query Count");
        student.setUsername("student-sdq");
        student.setAuthUserId(userId);
        student.setEmail("student-sdq@example.test");
        student.setStatus("active");
        entityManager.persist(student);

        for (int index = 1; index <= assignmentCount; index++) {
            Assignment assignment = new Assignment();
            assignment.setId("assignment-sdq-" + index);
            assignment.setTenantId(tenantId);
            assignment.setAcademicClassId(classId);
            assignment.setQuizId("qz_math_g6_base");
            assignment.setSubjectId("sub_math");
            assignment.setDueAt(now.plusDays(index));
            assignment.setStatus("active");
            assignment.setAssignedBy(userId);
            assignment.setRecipientMode("CLASS");
            entityManager.persist(assignment);

            if (index % 3 != 0) {
                Attempt attempt = new Attempt();
                attempt.setId("attempt-sdq-" + index);
                attempt.setTenantId(tenantId);
                attempt.setAssignmentId(assignment.getId());
                attempt.setQuizId(assignment.getQuizId());
                attempt.setStudentId(studentId);
                attempt.setStatus(index % 4 == 0 ? "in_progress" : "submitted");
                attempt.setPercent(index % 4 == 0 ? 35.0D : 88.0D);
                attempt.setScore(index % 4 == 0 ? 35.0D : 88.0D);
                attempt.setMaxScore(100.0D);
                attempt.setPassed(index % 4 != 0);
                attempt.setStartedAt(now.minusHours(index + 1L));
                attempt.setSubmittedAt(index % 4 == 0 ? null : now.minusHours(index));
                attempt.setUpdatedAt(now.minusHours(index));
                entityManager.persist(attempt);
            }
        }
    }
}
