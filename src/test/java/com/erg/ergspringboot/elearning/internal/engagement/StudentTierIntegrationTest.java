package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierTableRowResponse;
import com.erg.ergspringboot.elearning.internal.engagement.StudentTierService.Cohort;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Weekly tier job (V140): idempotent, own-data reads, frames only when unlocked, promotion shown once. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class StudentTierIntegrationTest {

    private static final String TENANT = "tenant-tier";
    private static final String CLASS_ID = "class-tier";
    private static final LocalDate WEEK = LocalDate.now(LeaderboardXp.ZONE).with(DayOfWeek.MONDAY);
    private static final OffsetDateTime LAST_SUNDAY = WEEK.atStartOfDay(LeaderboardXp.ZONE).minusHours(2).toOffsetDateTime();

    @Autowired
    private StudentTierService service;

    @Autowired
    private StudentTierReader reader;

    @Autowired
    private ClassLeaderboardService leaderboard;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private int attempts;

    @BeforeEach
    void seed() {
        EducationUnit school = new EducationUnit();
        school.setId("school-tier"); school.setTenantId(TENANT); school.setName("Tier School"); school.setType("school");
        entityManager.persist(school);
        user("usr-teacher");
        Classroom classroom = new Classroom();
        classroom.setId(CLASS_ID); classroom.setTenantId(TENANT); classroom.setSchoolId("school-tier"); classroom.setName(CLASS_ID);
        classroom.setGrade("10"); classroom.setAcademicYear("2026"); classroom.setStatus("active"); classroom.setHomeroomTeacherId("usr-teacher");
        entityManager.persist(classroom);
        for (String a : List.of("a1", "a2", "a3")) assignment(a);
        student("s-an", "An");
        student("s-binh", "Bình");
        student("s-cuong", "Cường");
        student("s-dung", "Dũng");
        for (String a : List.of("a1", "a2", "a3")) attempt("s-an", a, 20);   // 3 x 50 XP
        for (String a : List.of("a1", "a2", "a3")) attempt("s-binh", a, 4);  // 3 x 26 XP
        for (String a : List.of("a1", "a2")) attempt("s-cuong", a, 20);      // only two hand-ins: not ranked
        entityManager.flush();
    }

    @Test
    void jobRanksOnlyThoseWithThreeHandInsAndIsIdempotent() {
        Cohort cohort = new Cohort(TENANT, "school-tier", "10");
        service.refreshCohort(cohort, WEEK);
        service.refreshCohort(cohort, WEEK);
        assertThat(jdbc.queryForObject("select count(*) from student_tier_week where tenant_id = ? and week_start = ?", Integer.class, TENANT, WEEK))
                .isEqualTo(4);

        MyTierResponse an = reader.myTier(TENANT, "usr-s-an");
        MyTierResponse binh = reader.myTier(TENANT, "usr-s-binh");
        MyTierResponse cuong = reader.myTier(TENANT, "usr-s-cuong");
        assertThat(an.tierKey()).isNotNull();
        assertThat(an.gradeRank()).isEqualTo(1);
        assertThat(an.gradeSize()).isEqualTo(2);
        assertThat(an.eligible()).isTrue();
        assertThat(an.courseXp()).isGreaterThan(binh.courseXp());
        assertThat(TierMath.position(an.tierKey(), an.division())).isGreaterThan(TierMath.position(binh.tierKey(), binh.division()));
        assertThat(cuong.tierKey()).isNull();
        assertThat(cuong.eligible()).isFalse();
        assertThat(cuong.submittedCount()).isEqualTo(2);

        List<TierTableRowResponse> table = reader.tierTable(TENANT, "usr-s-an", null);
        assertThat(table).hasSize(8);
        assertThat(table.stream().mapToInt(TierTableRowResponse::seats).sum()).isEqualTo(2);
        assertThatThrownBy(() -> reader.tierTable(TENANT, "usr-s-an", "not-a-date")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void framesOpenWithTheTierAndCannotBeSelectedBeforeThat() {
        service.refreshCohort(new Cohort(TENANT, "school-tier", "10"), WEEK);
        String tier = reader.myTier(TENANT, "usr-s-an").tierKey();
        MyFramesResponse frames = reader.myFrames(TENANT, "usr-s-an");
        assertThat(frames.selected()).isEqualTo(tier); // default: the frame of the current tier
        assertThat(frames.frames()).filteredOn(frame -> frame.unlocked()).extracting(frame -> frame.tierKey()).containsExactly(tier);
        assertThat(frames.frames()).filteredOn(frame -> !frame.unlocked() && frame.tierKey().equals("ct"))
                .allSatisfy(frame -> assertThat(frame.unlockedAt()).isNull());

        String locked = frames.frames().stream().filter(frame -> !frame.unlocked()).findFirst().orElseThrow().tierKey();
        assertThatThrownBy(() -> reader.selectFrame(TENANT, "usr-s-an", locked)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reader.selectFrame(TENANT, "usr-s-an", "nope")).isInstanceOf(BadRequestException.class);

        assertThat(leaderboard.forStudent(TENANT, "usr-s-an", null, "COURSE", "CLASS").entries())
                .filteredOn(entry -> entry.isViewer()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.tierKey()).isEqualTo(tier);
                    assertThat(entry.frameKey()).isEqualTo(tier);
                });
        assertThat(reader.selectFrame(TENANT, "usr-s-an", null).selected()).isNull();
        assertThat(leaderboard.forStudent(TENANT, "usr-s-an", null, "COURSE", "CLASS").entries())
                .filteredOn(entry -> entry.isViewer()).singleElement().satisfies(entry -> assertThat(entry.frameKey()).isNull());
        assertThat(reader.selectFrame(TENANT, "usr-s-an", tier).selected()).isEqualTo(tier);
    }

    @Test
    void studentsCannotReadAnotherStudentsTierOrFrames() {
        service.refreshCohort(new Cohort(TENANT, "school-tier", "10"), WEEK);
        // Everything is keyed by the authenticated user: a user with no student row has no data at all.
        assertThatThrownBy(() -> reader.myTier(TENANT, "usr-teacher")).hasMessageContaining("student");
        assertThatThrownBy(() -> reader.selectFrame("other-tenant", "usr-s-an", null)).hasMessageContaining("student");
    }

    @Test
    void courseLeaderboardShowsMeAndSubmittedCounts() {
        var board = leaderboard.forStudent(TENANT, "usr-s-binh", null, "COURSE", "GRADE");
        assertThat(board.period()).isEqualTo("COURSE");
        assertThat(board.me().rank()).isEqualTo(3); // Cuong (two hand-ins) outranks Binh on XP
        assertThat(board.me().gradeRank()).isEqualTo(3);
        assertThat(board.me().xpToNextRank()).isPositive();
        assertThat(board.entries().getFirst().submittedInPeriod()).isEqualTo(3);
    }

    @Test
    void aTierPromotionIsShownOnceUntilAcknowledged() {
        String year = StudentTierService.schoolYear(LocalDate.now(LeaderboardXp.ZONE));
        insertWeek("s-dung", year, WEEK.minusWeeks(2), "dong", 3, 80, 100);
        insertWeek("s-dung", year, WEEK.minusWeeks(1), "bac", 5, 60, 100);
        insertWeek("s-dung", year, WEEK, "bac", 4, 50, 100); // a division up only: no promotion screen

        var promotion = reader.myTier(TENANT, "usr-s-dung").pendingPromotion();
        assertThat(promotion).isNotNull();
        assertThat(promotion.fromKey()).isEqualTo("dong");
        assertThat(promotion.toKey()).isEqualTo("bac");
        assertThat(promotion.weekStart()).isEqualTo(WEEK.minusWeeks(1).toString());
        assertThat(promotion.studentsPassed()).isEqualTo(20);
        assertThat(promotion.gradePercentile()).isEqualTo(60);

        assertThat(reader.acknowledgePromotion(TENANT, "usr-s-dung", promotion.weekStart())).isTrue();
        assertThat(reader.myTier(TENANT, "usr-s-dung").pendingPromotion()).isNull();
    }

    private void insertWeek(String student, String year, LocalDate week, String tier, int division, int rank, int size) {
        jdbc.update("""
                insert into student_tier_week (tenant_id, student_id, school_id, grade, school_year, week_start, course_xp,
                    submitted_count, grade_rank, grade_size, tier_key, division) values (?, ?, 'school-tier', '10', ?, ?, 100, 5, ?, ?, ?, ?)
                """, TENANT, student, year, week, rank, size, tier, division);
    }

    private void user(String id) {
        User user = new User();
        user.setId(id); user.setTenantId(TENANT); user.setEmail(id + "@example.test"); user.setFullName(id); user.setStatus("active");
        user.setAccountType("student"); user.setMustChangePassword(false); user.setIsProfileCompleted(true); user.setRecoveryEmailVerified(false);
        entityManager.persist(user);
    }

    private void assignment(String id) {
        Assignment assignment = new Assignment();
        assignment.setId(id); assignment.setTenantId(TENANT); assignment.setAcademicClassId(CLASS_ID); assignment.setQuizId("qz_math_g6_base");
        assignment.setSubjectId("sub_math"); assignment.setDueAt(LAST_SUNDAY.plusDays(30)); assignment.setStatus("active");
        assignment.setAssignedBy("usr-teacher"); assignment.setRecipientMode("CLASS");
        entityManager.persist(assignment);
    }

    private void student(String id, String name) {
        user("usr-" + id);
        Student student = new Student();
        student.setId(id); student.setTenantId(TENANT); student.setSchoolId("school-tier"); student.setAcademicClassId(CLASS_ID);
        student.setStudentCode(id.toUpperCase()); student.setFullName(name); student.setUsername(id); student.setAuthUserId("usr-" + id);
        student.setStatus("active");
        entityManager.persist(student);
    }

    private void attempt(String studentId, String assignmentId, int questions) {
        Attempt attempt = new Attempt();
        attempt.setId("att-tier-" + (++attempts)); attempt.setTenantId(TENANT); attempt.setAssignmentId(assignmentId);
        attempt.setQuizId("qz_math_g6_base"); attempt.setStudentId(studentId); attempt.setStatus("submitted");
        attempt.setTotalQuestions(questions); attempt.setLate(false); attempt.setPercent(50.0D); attempt.setScore(50.0D);
        attempt.setMaxScore(100.0D); attempt.setPassed(true); attempt.setStartedAt(LAST_SUNDAY.minusHours(1));
        attempt.setSubmittedAt(LAST_SUNDAY); attempt.setUpdatedAt(LAST_SUNDAY);
        entityManager.persist(attempt);
    }
}
