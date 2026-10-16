package com.erg.ergspringboot.service.elearning.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentEnrollment;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentEnrollmentRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.AnnouncementCardProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentCountProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardScoreSummaryProjection;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentDashboardReadServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ErgPolicyService policyService;

    @Mock
    private StudentDashboardAnnouncementReadPort announcementReadPort;

    @Mock
    private StudentEnrollmentRepository studentEnrollmentRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    private StudentDashboardReadService service;

    @BeforeEach
    void setUp() {
        service = new StudentDashboardReadService(
                assignmentRepository,
                attemptRepository,
                studentRepository,
                userRepository,
                policyService,
                announcementReadPort
        );
    }

    @Test
    void studentDashboardRejectsMissingPermissionBeforeRepositoryRead() {
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.studentDashboard(scope("tenant-a", "student-1", null, null)))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(assignmentRepository, attemptRepository, userRepository, studentRepository, announcementReadPort);
    }

    @Test
    void studentDashboardRejectsTenantSpoofBeforeRepositoryRead() {
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.studentDashboard(scope("tenant-a", "student-1", "tenant-b", null)))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(assignmentRepository, attemptRepository, userRepository, studentRepository, announcementReadPort);
    }

    @Test
    void studentDashboardRejectsOtherStudentBeforeRepositoryRead() {
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.studentDashboard(scope("tenant-a", "student-1", null, "student-2")))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(assignmentRepository, attemptRepository, userRepository, studentRepository, announcementReadPort);
    }

    @Test
    void studentDashboardReturnsSafeFirstSliceWithoutRawAttempts() {
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);
        when(userRepository.findWithDetailsByIdAndTenantId("student-1", "tenant-a")).thenReturn(Optional.empty());
        when(studentRepository.findFirstByTenantIdAndAuthUserId("tenant-a", "student-1")).thenReturn(Optional.empty());

        StudentDashboardResponse response = service.studentDashboard(scope("tenant-a", "student-1", null, null));

        assertThat(response.tenantId()).isEqualTo("tenant-a");
        assertThat(response.viewer().userId()).isEqualTo("student-1");
        assertThat(response.profile().userId()).isEqualTo("student-1");
        assertThat(response.courses()).isEmpty();
        assertThat(response.assignments()).isEmpty();
        assertThat(response.nextActions()).isEmpty();
        assertThat(response.announcements()).isEmpty();
        verifyNoInteractions(announcementReadPort);
    }

    @Test
    void studentDashboardReturnsBoundedProjectionDataAndCountersWithoutRawAnswers() {
        OffsetDateTime now = OffsetDateTime.now();
        Student student = new Student();
        student.setId("student-row-1");
        student.setAuthUserId("student-1");
        student.setAcademicClassId("class-1");
        student.setSchoolId("school-1");
        student.setFullName("Nguyen Van A");
        student.setEmail("student@example.com");
        student.setStatus("active");
        student.setUpdatedAt(now.minusDays(1));
        StudentDashboardAssignmentProjection math = new StudentDashboardAssignmentProjection(
                "assignment-1",
                "class-1",
                "quiz-1",
                "math",
                "Mathematics",
                "Algebra practice",
                now.plusDays(2),
                "active",
                now.minusDays(2),
                "attempt-1",
                "in_progress",
                40D,
                null,
                now.minusHours(2)
        );
        StudentDashboardAssignmentProjection science = new StudentDashboardAssignmentProjection(
                "assignment-2",
                "class-1",
                "quiz-2",
                "science",
                "Science",
                "Energy review",
                now.plusDays(5),
                "active",
                now.minusDays(3),
                null,
                null,
                null,
                null,
                null
        );
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);
        when(userRepository.findWithDetailsByIdAndTenantId("student-1", "tenant-a")).thenReturn(Optional.empty());
        when(studentRepository.findFirstByTenantIdAndAuthUserId("tenant-a", "student-1")).thenReturn(Optional.of(student));
        when(assignmentRepository.findStudentDashboardAssignments(
                "tenant-a",
                "class-1",
                "student-row-1",
                PageRequest.of(0, 8)
        )).thenReturn(List.of(math, science));
        when(assignmentRepository.summarizeStudentDashboardAssignments("tenant-a", "class-1", "student-row-1"))
                .thenReturn(new StudentDashboardAssignmentCountProjection(12L, 3L));
        when(attemptRepository.summarizeStudentDashboardScores("tenant-a", "student-row-1", "class-1"))
                .thenReturn(new StudentDashboardScoreSummaryProjection(4L, 2L, 3L, 72.4D, 98.8D));
        when(announcementReadPort.findStudentDashboardAnnouncements("tenant-a", "student-row-1", "school-1", "class-1", 5))
                .thenReturn(List.of(
                        new AnnouncementCardProjection(
                                "announcement-1",
                                "Exam schedule",
                                "Room 301 at 08:00",
                                true,
                                now.minusHours(6),
                                now.minusHours(1)
                        ),
                        new AnnouncementCardProjection(
                                "announcement-2",
                                "Reading reminder",
                                "Finish chapter 4 before class",
                                false,
                                now.minusDays(1),
                                now.minusDays(1)
                        )
                ));

        StudentDashboardResponse response = service.studentDashboard(scope("tenant-a", "student-1", null, null));

        assertThat(response.profile().studentId()).isEqualTo("student-row-1");
        assertThat(response.summary().enrolledCourseCount()).isEqualTo(2);
        assertThat(response.summary().activeAssignmentCount()).isEqualTo(12);
        assertThat(response.summary().pendingActionCount()).isEqualTo(3);
        assertThat(response.summary().scoreSummary().completedAttemptCount()).isEqualTo(4);
        assertThat(response.summary().scoreSummary().attemptedAssignmentCount()).isEqualTo(2);
        assertThat(response.summary().scoreSummary().passedAttemptCount()).isEqualTo(3);
        assertThat(response.summary().scoreSummary().averagePercent()).isEqualTo(72);
        assertThat(response.summary().scoreSummary().bestPercent()).isEqualTo(99);
        assertThat(response.assignments())
                .extracting("id")
                .containsExactly("assignment-1", "assignment-2");
        assertThat(response.assignments())
                .extracting("quizId")
                .containsExactly("quiz-1", "quiz-2");
        assertThat(response.assignments())
                .extracting("title")
                .containsExactly("Algebra practice", "Energy review");
        assertThat(response.courses())
                .extracting("id")
                .containsExactly("math", "science");
        assertThat(response.courses().getFirst().progressPercent()).isEqualTo(40);
        assertThat(response.nextActions())
                .extracting("targetId")
                .containsExactly("assignment-1", "assignment-2");
        assertThat(response.nextActions())
                .extracting("label")
                .containsExactly("Continue Algebra practice", "Start Energy review");
        assertThat(response.announcements())
                .extracting("id")
                .containsExactly("announcement-1", "announcement-2");
        assertThat(response.announcements())
                .extracting("title")
                .containsExactly("Exam schedule", "Reading reminder");
        assertThat(response.announcements())
                .extracting("content")
                .containsExactly("Room 301 at 08:00", "Finish chapter 4 before class");
        assertThat(response.announcements())
                .extracting("pinned")
                .containsExactly(true, false);

        verify(userRepository).findWithDetailsByIdAndTenantId("student-1", "tenant-a");
        verify(studentRepository).findFirstByTenantIdAndAuthUserId("tenant-a", "student-1");
        verify(assignmentRepository).findStudentDashboardAssignments(
                "tenant-a",
                "class-1",
                "student-row-1",
                PageRequest.of(0, 8)
        );
        verify(assignmentRepository).summarizeStudentDashboardAssignments("tenant-a", "class-1", "student-row-1");
        verify(attemptRepository).summarizeStudentDashboardScores("tenant-a", "student-row-1", "class-1");
        verify(announcementReadPort).findStudentDashboardAnnouncements("tenant-a", "student-row-1", "school-1", "class-1", 5);
        verifyNoMoreInteractions(userRepository, studentRepository, assignmentRepository, attemptRepository, announcementReadPort);
    }

    @Test
    void studentDashboardReadsRecipientAssignmentsEvenWhenClassIsMissing() {
        OffsetDateTime now = OffsetDateTime.now();
        Student student = new Student();
        student.setId("student-row-1");
        student.setAuthUserId("student-1");
        student.setFullName("Nguyen Van A");
        StudentDashboardAssignmentProjection assignment = new StudentDashboardAssignmentProjection(
                "assignment-1",
                "class-from-recipient",
                null,
                "math",
                "Mathematics",
                "Group practice",
                now.plusDays(2),
                "active",
                now.minusDays(2),
                null,
                null,
                null,
                null,
                null
        );
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);
        when(userRepository.findWithDetailsByIdAndTenantId("student-1", "tenant-a")).thenReturn(Optional.empty());
        when(studentRepository.findFirstByTenantIdAndAuthUserId("tenant-a", "student-1")).thenReturn(Optional.of(student));
        when(assignmentRepository.findStudentDashboardAssignments(
                "tenant-a",
                null,
                "student-row-1",
                PageRequest.of(0, 8)
        )).thenReturn(List.of(assignment));
        when(assignmentRepository.summarizeStudentDashboardAssignments("tenant-a", null, "student-row-1"))
                .thenReturn(new StudentDashboardAssignmentCountProjection(1L, 1L));
        when(attemptRepository.summarizeStudentDashboardScores("tenant-a", "student-row-1", null))
                .thenReturn(new StudentDashboardScoreSummaryProjection(0L, 0L, 0L, null, null));

        StudentDashboardResponse response = service.studentDashboard(scope("tenant-a", "student-1", null, null));

        assertThat(response.assignments())
                .extracting("id")
                .containsExactly("assignment-1");
        assertThat(response.assignments().getFirst().quizId()).isNull();
        assertThat(response.summary().activeAssignmentCount()).isEqualTo(1);
        assertThat(response.summary().pendingActionCount()).isEqualTo(1);
        verify(announcementReadPort, org.mockito.Mockito.never())
                .findStudentDashboardAnnouncements(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void studentDashboardSelectsRequestedEnrollmentAndScopesEveryAggregateToThatClass() {
        Student student = new Student();
        student.setId("student-row-1");
        student.setAuthUserId("student-1");
        student.setAcademicClassId("class-a");
        student.setSchoolId("school-1");

        StudentEnrollment classAEnrollment = enrollment("enrollment-a", "class-a", "2026-2027");
        StudentEnrollment classBEnrollment = enrollment("enrollment-b", "class-b", "2026-2027");
        Classroom classA = classroom("class-a", "6A1", "6");
        Classroom classB = classroom("class-b", "CLB Robotics", "Ngoáº¡i khÃ³a");

        StudentDashboardReadService multiClassService = new StudentDashboardReadService(
                assignmentRepository,
                attemptRepository,
                studentRepository,
                userRepository,
                policyService,
                announcementReadPort,
                studentEnrollmentRepository,
                classroomRepository
        );
        when(policyService.isAllowed(any(ErgPrincipal.class), eq("elearning.assignment.read_self"), eq("elearning"), eq("tenant-a")))
                .thenReturn(true);
        when(userRepository.findWithDetailsByIdAndTenantId("student-1", "tenant-a")).thenReturn(Optional.empty());
        when(studentRepository.findFirstByTenantIdAndAuthUserId("tenant-a", "student-1")).thenReturn(Optional.of(student));
        when(studentEnrollmentRepository.findByTenantIdAndStudentIdAndStatusIgnoreCaseOrderByCreatedAtAsc(
                "tenant-a", "student-row-1", "ENROLLED"))
                .thenReturn(List.of(classAEnrollment, classBEnrollment));
        when(classroomRepository.findByTenantIdAndIdIn(eq("tenant-a"), any()))
                .thenReturn(List.of(classA, classB));
        when(assignmentRepository.findStudentDashboardAssignments(
                "tenant-a", "class-b", "student-row-1", PageRequest.of(0, 8)))
                .thenReturn(List.of());
        when(attemptRepository.summarizeStudentDashboardScores("tenant-a", "student-row-1", "class-b"))
                .thenReturn(new StudentDashboardScoreSummaryProjection(1L, 1L, 1L, 88D, 88D));
        when(announcementReadPort.findStudentDashboardAnnouncements(
                "tenant-a", "student-row-1", "school-1", "class-b", 5))
                .thenReturn(List.of());

        StudentDashboardResponse response = multiClassService.studentDashboard(
                scope("tenant-a", "student-1", null, null, "class-b"));

        assertThat(response.selectedClassId()).isEqualTo("class-b");
        assertThat(response.profile().academicClassId()).isEqualTo("class-b");
        assertThat(response.classes())
                .extracting("classId")
                .containsExactly("class-a", "class-b");
        assertThat(response.classes())
                .extracting("className")
                .containsExactly("6A1", "CLB Robotics");
        assertThat(response.summary().scoreSummary().averagePercent()).isEqualTo(88);
        verify(assignmentRepository).summarizeStudentDashboardAssignments("tenant-a", "class-b", "student-row-1");
    }

    @Test
    void announcementContractsExcludeInternalPublishNotesAndTargetingRules() {
        assertThat(recordComponentNames(com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses
                .StudentDashboardAnnouncementCardResponse.class))
                .doesNotContain(
                        "tenantId",
                        "targetType",
                        "targetingRule",
                        "academicClassIds",
                        "studentIds",
                        "authorId",
                        "internalPublishNote"
                );
        assertThat(recordComponentNames(AnnouncementCardProjection.class))
                .doesNotContain(
                        "targetType",
                        "targetingRule",
                        "academicClassIds",
                        "studentIds",
                        "authorId",
                        "internalPublishNote"
                );
    }

    private List<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private StudentDashboardReadScope scope(
            String tenantId,
            String userId,
            String requestedTenantId,
            String requestedStudentUserId
    ) {
        return scope(tenantId, userId, requestedTenantId, requestedStudentUserId, null);
    }

    private StudentDashboardReadScope scope(
            String tenantId,
            String userId,
            String requestedTenantId,
            String requestedStudentUserId,
            String requestedClassId
    ) {
        return new StudentDashboardReadScope(
                userId,
                tenantId,
                requestedTenantId,
                requestedStudentUserId,
                List.of("student"),
                List.of("elearning.assignment.read_self"),
                List.of(),
                List.of("elearning"),
                "student",
                "self",
                requestedClassId
        );
    }

    private StudentEnrollment enrollment(String id, String classId, String academicYear) {
        StudentEnrollment enrollment = new StudentEnrollment();
        enrollment.setId(id);
        enrollment.setTenantId("tenant-a");
        enrollment.setSchoolId("school-1");
        enrollment.setStudentId("student-row-1");
        enrollment.setClassId(classId);
        enrollment.setAcademicYear(academicYear);
        enrollment.setStatus("ENROLLED");
        return enrollment;
    }

    private Classroom classroom(String id, String name, String grade) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTenantId("tenant-a");
        classroom.setSchoolId("school-1");
        classroom.setName(name);
        classroom.setGrade(grade);
        classroom.setAcademicYear("2026-2027");
        classroom.setStatus("ACTIVE");
        return classroom;
    }
}
