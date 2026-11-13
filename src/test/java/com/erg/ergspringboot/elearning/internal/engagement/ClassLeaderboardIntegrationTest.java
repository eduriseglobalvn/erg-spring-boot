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
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** "Thi đua" board (V138, V139): XP ranking, grade scope, hiding oneself, own-class scope, per-class switch. */
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
        assignment("a3", CLASS_A);
        assignment("b1", CLASS_B);
        OffsetDateTime lastWeek = LeaderboardXp.window(LeaderboardXp.Period.WEEK, ZonedDateTime.now(LeaderboardXp.ZONE))
                .previous(LeaderboardXp.Period.WEEK).start().plusHours(1).toOffsetDateTime();

        student("s-an", "An", CLASS_A);
        attempt("s-an", "a1", "submitted", 10, false, NOW.minusMinutes(5)); // 25 + 10 before the deadline
        attempt("s-an", "a1", "submitted", 10, false, NOW.minusMinutes(4)); // a retake earns nothing
        attempt("s-an", "a2", "submitted", 20, false, NOW.minusMinutes(3)); // 40 + 10
        student("s-binh", "Bình", CLASS_A);
        attempt("s-binh", "a1", "submitted", 10, true, NOW.minusMinutes(3)); // late: 25
        student("s-cuong", "Cường", CLASS_A);
        attempt("s-cuong", "a1", "submitted", null, false, NOW.minusMinutes(3)); // 10 + 10
        attempt("s-cuong", "a2", "in_progress", 20, false, null); // not handed in
        attempt("s-cuong", "a3", "submitted", 20, false, lastWeek); // last week's leader
        student("s-dung", "Dũng", CLASS_A);
        // Primary class B, enrolled in A: listed in both; XP belongs to the student, from every class.
        student("s-em", "Em", CLASS_B);
        enrollment("s-em", CLASS_A);
        attempt("s-em", "a2", "submitted", 10, false, NOW.minusMinutes(2));
        attempt("s-em", "b1", "submitted", 10, false, NOW.minusMinutes(2));
        student("s-other", "Giang", CLASS_B);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void ranksByXpWithoutScoresAndUnrankedLast() {
        ClassLeaderboardResponse board = service.forStudent(TENANT, "usr-s-an", null, null, null);

        assertThat(board.classId()).isEqualTo(CLASS_A);
        assertThat(board.enabled()).isTrue();
        assertThat(board.period()).isEqualTo("WEEK");
        assertThat(board.scope()).isEqualTo("CLASS");
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::studentName)
                .containsExactly("An", "Em", "Bình", "Cường", "Dũng");
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::xp).containsExactly(85, 70, 25, 20, 0);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::rank).containsExactly(1, 2, 3, 4, null);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::streakDays).containsExactly(1, 1, 1, 1, 0);
        // Cường led last week alone and is 4th now; nobody else was ranked last week.
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::rankChange)
                .containsExactly(null, null, null, -3, null);
        assertThat(board.entries()).extracting(ClassLeaderboardEntryResponse::isViewer)
                .containsExactly(true, false, false, false, false);
    }

    @Test
    void gradeBoardListsEveryClassOfTheGrade() {
        ClassLeaderboardResponse grade = service.forStudent(TENANT, "usr-s-an", null, "week", "grade");
        assertThat(grade.scope()).isEqualTo("GRADE");
        assertThat(grade.entries()).extracting(ClassLeaderboardEntryResponse::studentName)
                .containsExactly("An", "Em", "Bình", "Cường", "Dũng", "Giang");
    }

    @Test
    void aHiddenStudentKeepsTheirOwnRankButClassmatesAndNotTeachersLoseThem() {
        service.setHidden(TENANT, "usr-s-binh", true);

        ClassLeaderboardResponse own = service.forStudent(TENANT, "usr-s-binh", null, null, null);
        assertThat(own.viewerHidden()).isTrue();
        assertThat(own.entries()).filteredOn(ClassLeaderboardEntryResponse::isViewer)
                .extracting(ClassLeaderboardEntryResponse::rank).containsExactly(3);
        assertThat(service.forStudent(TENANT, "usr-s-an", null, null, null).entries())
                .extracting(ClassLeaderboardEntryResponse::studentName).doesNotContain("Bình");
        assertThat(service.forManager(new Manager(TENANT, TEACHER, false), CLASS_A, null).entries())
                .extracting(ClassLeaderboardEntryResponse::studentName).contains("Bình");
    }

    @Test
    void studentOnlyOpensOwnClasses() {
        assertThatThrownBy(() -> service.forStudent(TENANT, "usr-s-an", CLASS_B, null, null)).isInstanceOf(ForbiddenException.class);
        ClassLeaderboardResponse enrolled = service.forStudent(TENANT, "usr-s-em", CLASS_A, null, null);
        assertThat(enrolled.entries()).filteredOn(ClassLeaderboardEntryResponse::isViewer)
                .extracting(ClassLeaderboardEntryResponse::studentName).containsExactly("Em");
        assertThat(service.forStudent(TENANT, "usr-s-em", null, null, null).classId()).isEqualTo(CLASS_B);
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
        ClassLeaderboardResponse student = service.forStudent(TENANT, "usr-s-an", null, null, null);
        assertThat(student.enabled()).isFalse();
        assertThat(student.entries()).isEmpty();
        ClassLeaderboardResponse forTeacher = service.forManager(teacher, CLASS_A, null);
        assertThat(forTeacher.enabled()).isFalse();
        assertThat(forTeacher.entries()).hasSize(5);

        service.setEnabled(teacher, CLASS_A, true);
        assertThat(service.forStudent(TENANT, "usr-s-an", null, null, null).entries()).hasSize(5);
    }

    @Test
    void teacherOutsideTheClassIsForbiddenAndTenantAdminSeesEveryClass() {
        Manager other = new Manager(TENANT, OTHER_TEACHER, false);
        assertThatThrownBy(() -> service.forManager(other, CLASS_A, null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.setEnabled(other, CLASS_A, false)).isInstanceOf(ForbiddenException.class);
        Manager admin = new Manager(TENANT, "usr-lb-admin", true);
        assertThatThrownBy(() -> service.forManager(admin, "cls-of-another-tenant", null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.setEnabled(admin, "cls-of-another-tenant", false)).isInstanceOf(ForbiddenException.class);
        assertThat(service.classes(admin))
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

    private void attempt(String studentId, String assignmentId, String status, Integer questions, boolean late,
                         OffsetDateTime submittedAt) {
        Attempt attempt = new Attempt();
        attempt.setId("att-lb-" + (++attempts));
        attempt.setTenantId(TENANT);
        attempt.setAssignmentId(assignmentId);
        attempt.setQuizId("qz_math_g6_base");
        attempt.setStudentId(studentId);
        attempt.setStatus(status);
        attempt.setTotalQuestions(questions);
        attempt.setLate(late);
        attempt.setPercent(50.0D);
        attempt.setScore(50.0D);
        attempt.setMaxScore(100.0D);
        attempt.setPassed(true);
        attempt.setStartedAt(NOW.minusHours(1));
        attempt.setSubmittedAt(submittedAt);
        attempt.setUpdatedAt(submittedAt == null ? NOW : submittedAt);
        entityManager.persist(attempt);
    }
}
