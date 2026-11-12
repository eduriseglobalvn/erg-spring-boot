package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardEntryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardResponse;
import com.erg.ergspringboot.elearning.internal.engagement.ClassLeaderboardService.Manager;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.ClassLeaderboardRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollment;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** V138 class leaderboard: ranking rule (latest hand-in per assignment), student own-class scope, per-class switch. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ClassLeaderboardIntegrationTest {

    private static final String TENANT = "tenant-lb";
    private static final String CLASS_A = "class-lb-a";
    private static final String CLASS_B = "class-lb-b";
    private static final String TEACHER = "usr-lb-teacher";
    private static final String OTHER_TEACHER = "usr-lb-other-teacher";
    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Autowired
    private ClassLeaderboardService service;

    @Autowired
    private ClassLeaderboardRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seed() {
        EducationUnit school = new EducationUnit();
        school.setId("school-lb"); school.setTenantId(TENANT); school.setName("Leaderboard School"); school.setType("school");
        entityManager.persist(school);
        user(TEACHER);
        user(OTHER_TEACHER);
        classroom(CLASS_A, TEACHER);
        classroom(CLASS_B, OTHER_TEACHER);
        assignment("a1", CLASS_A);
        assignment("a2", CLASS_A);
        assignment("b1", CLASS_B);

        student("s-an", "An", CLASS_A);
        attempt("s-an", "a1", "submitted", 40.0, 3); // older hand-in, replaced by the one below
        attempt("s-an", "a1", "submitted", 90.0, 2);
        attempt("s-an", "a2", "submitted", 80.0, 1);
        student("s-binh", "Bình", CLASS_A);
        attempt("s-binh", "a1", "submitted", 85.0, 1);
        student("s-cuong", "Cường", CLASS_A);
        attempt("s-cuong", "a1", "submitted", 100.0, 3);
        attempt("s-cuong", "a1", "submitted", 50.0, 2); // latest hand-in counts, not the best
        attempt("s-cuong", "a2", "in_progress", 100.0, 1); // not handed in
        student("s-dung", "Dũng", CLASS_A);
        // Primary class B, enrolled in A: listed in both.
        student("s-em", "Em", CLASS_B);
        enrollment("s-em", CLASS_A);
        attempt("s-em", "a2", "submitted", 70.0, 1);
        attempt("s-em", "b1", "submitted", 10.0, 1);
        student("s-other", "Giang", CLASS_B);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void ranksByAverageThenCompletedThenNameWithUnrankedLast() {
        ClassLeaderboardResponse board = service.forStudent(TENANT, "usr-s-an", null);

        assertThat(board.classId()).isEqualTo(CLASS_A);
        assertThat(board.enabled()).isTrue();
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::studentName)
                .containsExactly("An", "Bình", "Em", "Cường", "Dũng");
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::rank).containsExactly(1, 2, 3, 4, null);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::averagePercent)
                .containsExactly(85.0, 85.0, 70.0, 50.0, null);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::completedCount)
                .containsExactly(2L, 1L, 1L, 1L, 0L);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::isViewer)
                .containsExactly(true, false, false, false, false);
    }

    @Test
    void studentOnlyOpensOwnClasses() {
        assertThatThrownBy(() -> service.forStudent(TENANT, "usr-s-an", CLASS_B)).isInstanceOf(ForbiddenException.class);
        ClassLeaderboardResponse enrolled = service.forStudent(TENANT, "usr-s-em", CLASS_A);
        assertThat(enrolled.entries()).filteredOn(ClassLeaderboardEntryResponse::isViewer)
                .extracting(ClassLeaderboardEntryResponse::studentName).containsExactly("Em");
        assertThat(service.forStudent(TENANT, "usr-s-em", null).classId()).isEqualTo(CLASS_B);
    }

    @Test
    void switchedOffLeaderboardShowsStudentsNothingButTeachersEverything() {
        Manager teacher = new Manager(TENANT, TEACHER, false);
        assertThat(service.classes(teacher)).singleElement()
                .satisfies(row -> assertThat(row.leaderboardEnabled()).isTrue());
        assertThat(repository.findLeaderboardEnabled(TENANT, CLASS_A)).contains(true);

        service.setEnabled(teacher, CLASS_A, false);
        entityManager.clear();

        assertThat(repository.findLeaderboardEnabled(TENANT, CLASS_A)).contains(false);
        ClassLeaderboardResponse student = service.forStudent(TENANT, "usr-s-an", null);
        assertThat(student.enabled()).isFalse();
        assertThat(student.entries()).isEmpty();
        ClassLeaderboardResponse forTeacher = service.forManager(teacher, CLASS_A);
        assertThat(forTeacher.enabled()).isFalse();
        assertThat(forTeacher.entries()).hasSize(5);

        service.setEnabled(teacher, CLASS_A, true);
        assertThat(service.forStudent(TENANT, "usr-s-an", null).entries()).hasSize(5);
    }

    @Test
    void teacherOutsideTheClassIsForbiddenAndTenantAdminSeesEveryClass() {
        Manager other = new Manager(TENANT, OTHER_TEACHER, false);
        assertThatThrownBy(() -> service.forManager(other, CLASS_A)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.setEnabled(other, CLASS_A, false)).isInstanceOf(ForbiddenException.class);
        assertThat(service.classes(new Manager(TENANT, "usr-lb-admin", true)))
                .extracting(row -> row.id()).containsExactly(CLASS_A, CLASS_B);
    }

    private void user(String id) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT);
        user.setEmail(id + "@example.test");
        user.setFullName(id);
        user.setStatus("active");
        user.setAccountType("student");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        entityManager.persist(user);
    }

    private void classroom(String id, String homeroomTeacher) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTenantId(TENANT);
        classroom.setSchoolId("school-lb");
        classroom.setName(id);
        classroom.setGrade("10");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        classroom.setHomeroomTeacherId(homeroomTeacher);
        entityManager.persist(classroom);
    }

    private void assignment(String id, String classId) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setTenantId(TENANT);
        assignment.setAcademicClassId(classId);
        assignment.setQuizId("qz_math_g6_base");
        assignment.setSubjectId("sub_math");
        assignment.setDueAt(NOW.plusDays(1));
        assignment.setStatus("active");
        assignment.setAssignedBy(classId.equals(CLASS_A) ? TEACHER : OTHER_TEACHER);
        assignment.setRecipientMode("CLASS");
        entityManager.persist(assignment);
    }

    private void student(String id, String name, String classId) {
        user("usr-" + id);
        Student student = new Student();
        student.setId(id);
        student.setTenantId(TENANT);
        student.setSchoolId("school-lb");
        student.setAcademicClassId(classId);
        student.setStudentCode(id.toUpperCase());
        student.setFullName(name);
        student.setUsername(id);
        student.setAuthUserId("usr-" + id);
        student.setStatus("active");
        entityManager.persist(student);
    }

    private void enrollment(String studentId, String classId) {
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setId("enr-" + studentId + "-" + classId);
        enrollment.setTenantId(TENANT);
        enrollment.setSchoolId("school-lb");
        enrollment.setStudentId(studentId);
        enrollment.setClassId(classId);
        enrollment.setStatus("ENROLLED");
        entityManager.persist(enrollment);
    }

    private int attempts;

    private void attempt(String studentId, String assignmentId, String status, double percent, int hoursAgo) {
        Attempt attempt = new Attempt();
        attempt.setId("att-lb-" + (++attempts));
        attempt.setTenantId(TENANT);
        attempt.setAssignmentId(assignmentId);
        attempt.setQuizId("qz_math_g6_base");
        attempt.setStudentId(studentId);
        attempt.setStatus(status);
        attempt.setPercent(percent);
        attempt.setScore(percent);
        attempt.setMaxScore(100.0D);
        attempt.setPassed(percent >= 50);
        attempt.setStartedAt(NOW.minusHours(hoursAgo + 1L));
        attempt.setSubmittedAt("submitted".equals(status) ? NOW.minusHours(hoursAgo) : null);
        attempt.setUpdatedAt(NOW.minusHours(hoursAgo));
        entityManager.persist(attempt);
    }
}
