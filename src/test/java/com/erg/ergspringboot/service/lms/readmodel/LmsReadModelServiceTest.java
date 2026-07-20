package com.erg.ergspringboot.service.lms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.model.lms.StudentGroup;
import com.erg.ergspringboot.model.lms.StudentGroupMember;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.AssignmentCardProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.AssignmentResourceProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassRiskSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassScoreSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import com.erg.ergspringboot.repository.lms.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.OverdueStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection;
import com.erg.ergspringboot.repository.lms.projection.RecipientProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentRosterProjection;
import com.erg.ergspringboot.repository.lms.projection.TeacherHomeworkSummaryProjection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LmsReadModelServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentResourceRepository assignmentResourceRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentGroupRepository studentGroupRepository;

    @Mock
    private StudentGroupMemberRepository studentGroupMemberRepository;

    private LmsReadModelService service;
    private ErgReadTaskExecutor readTaskExecutor;

    @BeforeEach
    void setUp() {
        readTaskExecutor = new ErgReadTaskExecutor(4, 4, 32, Duration.ofSeconds(1));
        service = new LmsReadModelService(
                assignmentRepository,
                assignmentResourceRepository,
                attemptRepository,
                classroomRepository,
                studentRepository,
                studentGroupRepository,
                studentGroupMemberRepository,
                readTaskExecutor
        );
    }

    @AfterEach
    void tearDown() {
        readTaskExecutor.close();
    }

    @Test
    void readModelServiceIsReadOnlyAndDelegatesAggregateProgressQuery() {
        Transactional transactional = LmsReadModelService.class.getAnnotation(Transactional.class);
        AssignmentProgressProjection expected = new AssignmentProgressProjection(2L, 1L, 0L);

        when(attemptRepository.summarizeAssignmentProgress("erg", "assignment-1")).thenReturn(expected);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(service.assignmentProgress("erg", "assignment-1")).isSameAs(expected);
        verify(attemptRepository).summarizeAssignmentProgress("erg", "assignment-1");
    }

    @Test
    void studentGroupsReturnsScopedGroupsWithMemberIdsWithoutNestedLookups() {
        OffsetDateTime now = OffsetDateTime.now();
        StudentGroup ownedGroup = new StudentGroup("erg", "Owned group", "class-1", "Owner note", "#0ea5e9", "teacher-1", 2);
        ownedGroup.setId("group-1");
        ownedGroup.setCreatedAt(now.minusDays(1));
        ownedGroup.setUpdatedAt(now);
        StudentGroup otherClassGroup = new StudentGroup("erg", "Other class", "class-2", null, null, "teacher-2", 1);
        otherClassGroup.setId("group-2");
        otherClassGroup.setCreatedAt(now.minusDays(2));
        StudentGroup ownedNoClassGroup = new StudentGroup("erg", "Owner ad hoc", null, null, null, "teacher-1", 1);
        ownedNoClassGroup.setId("group-3");
        ownedNoClassGroup.setCreatedAt(now.minusDays(3));
        when(studentGroupRepository.findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(
                eq("erg"), eq("teacher-1"), eq(Set.of("class-1")), any(Pageable.class)))
                .thenReturn(List.of(ownedGroup, otherClassGroup, ownedNoClassGroup));

        StudentGroupMember memberA = new StudentGroupMember("group-1", "student-1", "erg", now);
        StudentGroupMember memberB = new StudentGroupMember("group-1", "student-2", "erg", now);
        StudentGroupMember memberC = new StudentGroupMember("group-3", "student-3", "erg", now);
        when(studentGroupMemberRepository.findByTenantIdAndGroupIdIn(
                eq("erg"), eq(List.of("group-1", "group-3")), any(Pageable.class)))
                .thenReturn(List.of(memberA, memberB, memberC));

        StudentGroupWorkspaceResponse response = service.studentGroups(
                " erg ",
                null,
                List.of("class-1"),
                " teacher-1 "
        );

        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.groups()).hasSize(2);
        assertThat(response.groups())
                .extracting("id")
                .containsExactly("group-1", "group-3");
        assertThat(response.groups().getFirst().studentIds()).containsExactly("student-1", "student-2");
        assertThat(response.groups().getFirst().studentCount()).isEqualTo(2);
        assertThat(response.groups().getFirst().note()).isEqualTo("Owner note");
        assertThat(response.groups().getFirst().color()).isEqualTo("#0ea5e9");
        assertThat(response.groups().get(1).studentIds()).containsExactly("student-3");
        verify(studentGroupRepository).findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(
                eq("erg"), eq("teacher-1"), eq(Set.of("class-1")), any(Pageable.class));
        verify(studentGroupMemberRepository).findByTenantIdAndGroupIdIn(
                eq("erg"), eq(List.of("group-1", "group-3")), any(Pageable.class));
        verifyNoMoreInteractions(classroomRepository, studentRepository, assignmentRepository, attemptRepository);
    }

    @Test
    void assignmentProgressWorkspaceUsesAssignmentDetailSummaryAndAttemptPageProjections() {
        OffsetDateTime now = OffsetDateTime.now();
        AssignmentDetailProjection assignment = new AssignmentDetailProjection(
                "assignment-1",
                "class-1",
                "quiz-1",
                "math",
                now.plusDays(3),
                "active",
                "teacher-1",
                "CLASS",
                now.minusDays(1),
                now
        );
        AttemptSummaryProjection attempt = new AttemptSummaryProjection(
                "attempt-1",
                "assignment-1",
                "quiz-1",
                "student-1",
                "submitted",
                8.5D,
                10.0D,
                84.6D,
                true,
                now.minusHours(3),
                now.minusHours(1),
                now
        );
        PageRequest clampedAttemptPage = PageRequest.of(3, 50, Sort.by("updatedAt").descending());
        PageRequest recipientPage = PageRequest.of(0, 50);
        RecipientProgressProjection recipient = new RecipientProgressProjection(
                "student-1",
                "school-1",
                "class-1",
                "S001",
                "Nguyen Van A",
                "s001",
                "auth-1",
                "student1@example.com",
                "active",
                now
        );
        RecipientProgressProjection missingRecipient = new RecipientProgressProjection(
                "student-2",
                "school-1",
                "class-1",
                "S002",
                "Tran Thi B",
                "s002",
                "auth-2",
                "student2@example.com",
                "active",
                now
        );
        AttemptScoreProjection latestAttempt = new AttemptScoreProjection(
                "attempt-latest",
                "assignment-1",
                "quiz-1",
                "student-1",
                "in_progress",
                7.5D,
                10.0D,
                75.2D,
                false,
                now.minusHours(2),
                null,
                now.minusMinutes(5)
        );
        AttemptScoreProjection bestAttempt = new AttemptScoreProjection(
                "attempt-best",
                "assignment-1",
                "quiz-1",
                "student-1",
                "submitted",
                9.2D,
                10.0D,
                92.4D,
                true,
                now.minusHours(4),
                now.minusHours(3),
                now.minusHours(3)
        );
        MissingLateStudentProjection missingLateStudent = new MissingLateStudentProjection(
                "student-2",
                "school-1",
                "class-1",
                "S002",
                "Tran Thi B",
                "s002",
                "auth-2",
                "student2@example.com",
                "active",
                now.minusDays(1),
                true,
                true,
                now
        );
        when(assignmentRepository.findAssignmentDetail("erg", "assignment-1"))
                .thenReturn(Optional.of(assignment));
        when(assignmentResourceRepository.findResourceProjections("erg", "assignment-1"))
                .thenReturn(List.of(new AssignmentResourceProjection("assignment-1", "quiz-1", "version-1", "v1", 0)));
        when(attemptRepository.summarizeAssignmentProgress("erg", "assignment-1"))
                .thenReturn(new AssignmentProgressProjection(7L, 2L, 1L));
        when(attemptRepository.findAssignmentAttemptSummaries("erg", "assignment-1", "submitted", clampedAttemptPage))
                .thenReturn(new PageImpl<>(List.of(attempt), clampedAttemptPage, 151));
        when(assignmentRepository.findAssignmentRecipients("erg", "assignment-1", recipientPage))
                .thenReturn(new PageImpl<>(List.of(recipient, missingRecipient), recipientPage, 2));
        when(attemptRepository.findLatestAttemptScoresForRecipients(
                "erg",
                "assignment-1",
                List.of("student-1", "student-2")
        )).thenReturn(List.of(latestAttempt));
        when(attemptRepository.findBestAttemptScoresForRecipients(
                "erg",
                "assignment-1",
                List.of("student-1", "student-2")
        )).thenReturn(List.of(bestAttempt));
        when(assignmentRepository.findAssignmentMissingLateStudents("erg", "assignment-1", recipientPage))
                .thenReturn(new PageImpl<>(List.of(missingLateStudent), recipientPage, 1));

        AssignmentProgressWorkspaceResponse response = service.assignmentProgressWorkspace(
                " erg ",
                " assignment-1 ",
                " SUBMITTED ",
                3,
                500
        );

        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.assignment().id()).isEqualTo("assignment-1");
        assertThat(response.assignment().assignedBy()).isEqualTo("teacher-1");
        assertThat(response.assignment().resources()).hasSize(1);
        assertThat(response.assignment().resources().getFirst().quizVersionId()).isEqualTo("version-1");
        assertThat(response.summary().submittedCount()).isEqualTo(7);
        assertThat(response.summary().inProgressCount()).isEqualTo(2);
        assertThat(response.summary().needsReviewCount()).isEqualTo(1);
        assertThat(response.attempts().size()).isEqualTo(50);
        assertThat(response.attempts().totalItems()).isEqualTo(151);
        assertThat(response.attempts().items()).hasSize(1);
        assertThat(response.attempts().items().getFirst().percent()).isEqualTo(85);
        assertThat(response.attempts().items().getFirst().studentId()).isEqualTo("student-1");
        assertThat(response.recipients().size()).isEqualTo(50);
        assertThat(response.recipients().totalItems()).isEqualTo(2);
        assertThat(response.recipients().items()).hasSize(2);
        assertThat(response.recipients().items().getFirst().studentId()).isEqualTo("student-1");
        assertThat(response.recipients().items().getFirst().latestAttempt().id()).isEqualTo("attempt-latest");
        assertThat(response.recipients().items().getFirst().latestAttempt().percent()).isEqualTo(75);
        assertThat(response.recipients().items().getFirst().bestAttempt().id()).isEqualTo("attempt-best");
        assertThat(response.recipients().items().getFirst().bestAttempt().percent()).isEqualTo(92);
        assertThat(response.recipients().items().getFirst().missing()).isFalse();
        assertThat(response.recipients().items().get(1).studentId()).isEqualTo("student-2");
        assertThat(response.recipients().items().get(1).latestAttempt()).isNull();
        assertThat(response.recipients().items().get(1).bestAttempt()).isNull();
        assertThat(response.recipients().items().get(1).missing()).isTrue();
        assertThat(response.recipients().items().get(1).late()).isTrue();
        assertThat(response.missingLateStudents().items()).hasSize(1);
        assertThat(response.missingLateStudents().items().getFirst().studentId()).isEqualTo("student-2");
        assertThat(response.missingLateStudents().items().getFirst().dueAt()).isEqualTo(now.minusDays(1));
        verify(assignmentRepository).findAssignmentDetail("erg", "assignment-1");
        verify(assignmentResourceRepository).findResourceProjections("erg", "assignment-1");
        verify(assignmentRepository).findAssignmentRecipients("erg", "assignment-1", recipientPage);
        verify(assignmentRepository).findAssignmentMissingLateStudents("erg", "assignment-1", recipientPage);
        verify(attemptRepository).summarizeAssignmentProgress("erg", "assignment-1");
        verify(attemptRepository).findAssignmentAttemptSummaries("erg", "assignment-1", "submitted", clampedAttemptPage);
        verify(attemptRepository).findLatestAttemptScoresForRecipients(
                "erg",
                "assignment-1",
                List.of("student-1", "student-2")
        );
        verify(attemptRepository).findBestAttemptScoresForRecipients(
                "erg",
                "assignment-1",
                List.of("student-1", "student-2")
        );
        verifyNoMoreInteractions(classroomRepository, studentRepository, assignmentRepository, assignmentResourceRepository, attemptRepository);
    }

    @Test
    void classWorkspaceReadModelNormalizesStatusAndDelegatesProjectionQuery() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ClassWorkspaceProjection> expected = new PageImpl<>(java.util.List.of());

        when(classroomRepository.findSchoolClassWorkspaces("erg", "school-1", "active", pageable))
                .thenReturn(expected);

        assertThat(service.schoolClassWorkspaces("erg", "school-1", " ACTIVE ", pageable)).isSameAs(expected);
        verify(classroomRepository).findSchoolClassWorkspaces("erg", "school-1", "active", pageable);
    }

    @Test
    void classWorkspaceReadModelTreatsBlankStatusAsUnfiltered() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ClassWorkspaceProjection> expected = new PageImpl<>(java.util.List.of());

        when(classroomRepository.findSchoolClassWorkspaces("erg", "school-1", null, pageable))
                .thenReturn(expected);

        assertThat(service.schoolClassWorkspaces("erg", "school-1", " ", pageable)).isSameAs(expected);
        verify(classroomRepository).findSchoolClassWorkspaces("erg", "school-1", null, pageable);
    }

    @Test
    void classWorkspaceMapsProjectionToDtoAndClampsStudentPageSize() {
        OffsetDateTime now = OffsetDateTime.now();
        ClassWorkspaceProjection classProjection = new ClassWorkspaceProjection(
                "class-1",
                "school-1",
                "10A1",
                "10",
                "2026",
                "active",
                "teacher-1",
                48L,
                12L,
                now
        );
        StudentRosterProjection studentProjection = new StudentRosterProjection(
                "student-1",
                "school-1",
                "class-1",
                "S001",
                "Nguyen Van A",
                "s001",
                "auth-1",
                "student@example.com",
                "active",
                now
        );
        AssignmentCardProjection assignmentProjection = new AssignmentCardProjection(
                "assignment-1",
                "class-1",
                "quiz-1",
                "math",
                now.plusDays(7),
                "active",
                "teacher-1",
                "CLASS",
                now.minusDays(1),
                now
        );
        PageRequest clampedPage = PageRequest.of(
                2,
                50,
                Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending())
        );
        PageRequest clampedAssignmentPage = PageRequest.of(1, 50);
        when(classroomRepository.findClassWorkspace("erg", "class-1", "school-1"))
                .thenReturn(Optional.of(classProjection));
        when(studentRepository.findClassRoster("erg", "class-1", "active", clampedPage))
                .thenReturn(new PageImpl<>(java.util.List.of(studentProjection), clampedPage, 101));
        when(assignmentRepository.findClassAssignmentCards("erg", "class-1", "active", clampedAssignmentPage))
                .thenReturn(new PageImpl<>(java.util.List.of(assignmentProjection), clampedAssignmentPage, 51));
        when(assignmentResourceRepository.findResourceProjectionsByAssignmentIds("erg", List.of("assignment-1")))
                .thenReturn(List.of(new AssignmentResourceProjection("assignment-1", "quiz-1", "version-1", "v1", 0)));

        ClassWorkspaceResponse response = service.classWorkspace(
                "erg",
                "class-1",
                "school-1",
                " ACTIVE ",
                2,
                500,
                " ACTIVE ",
                1,
                500
        );

        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.classInfo().id()).isEqualTo("class-1");
        assertThat(response.classInfo().studentCount()).isEqualTo(48);
        assertThat(response.classInfo().assignmentCount()).isEqualTo(12);
        assertThat(response.students().size()).isEqualTo(50);
        assertThat(response.students().totalItems()).isEqualTo(101);
        assertThat(response.students().items()).hasSize(1);
        assertThat(response.students().items().getFirst().fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.assignments().size()).isEqualTo(50);
        assertThat(response.assignments().totalItems()).isEqualTo(51);
        assertThat(response.assignments().items()).hasSize(1);
        assertThat(response.assignments().items().getFirst().id()).isEqualTo("assignment-1");
        assertThat(response.assignments().items().getFirst().resources()).hasSize(1);
        assertThat(response.assignments().items().getFirst().resources().getFirst().quizVersionId()).isEqualTo("version-1");
        verify(classroomRepository).findClassWorkspace("erg", "class-1", "school-1");
        verify(studentRepository).findClassRoster("erg", "class-1", "active", clampedPage);
        verify(assignmentRepository).findClassAssignmentCards("erg", "class-1", "active", clampedAssignmentPage);
        verify(assignmentResourceRepository).findResourceProjectionsByAssignmentIds("erg", List.of("assignment-1"));
    }

    @Test
    void classWorkspaceUsesOneClassProjectionOneRosterPageAndOneAssignmentPageWithoutNestedLookups() {
        OffsetDateTime now = OffsetDateTime.now();
        ClassWorkspaceProjection classProjection = new ClassWorkspaceProjection(
                "class-1",
                "school-1",
                "10A1",
                "10",
                "2026",
                "active",
                "teacher-1",
                3L,
                7L,
                now
        );
        List<StudentRosterProjection> roster = List.of(
                new StudentRosterProjection(
                        "student-1",
                        "school-1",
                        "class-1",
                        "S001",
                        "Nguyen Van A",
                        "s001",
                        "auth-1",
                        "a@example.com",
                        "active",
                        now
                ),
                new StudentRosterProjection(
                        "student-2",
                        "school-1",
                        "class-1",
                        "S002",
                        "Tran Thi B",
                        "s002",
                        "auth-2",
                        "b@example.com",
                        "active",
                        now
                ),
                new StudentRosterProjection(
                        "student-3",
                        "school-1",
                        "class-1",
                        "S003",
                        "Le Van C",
                        "s003",
                        "auth-3",
                        "c@example.com",
                        "active",
                        now
                )
        );
        PageRequest rosterPage = PageRequest.of(
                0,
                20,
                Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending())
        );
        when(classroomRepository.findClassWorkspace("erg", "class-1", null))
                .thenReturn(Optional.of(classProjection));
        when(studentRepository.findClassRoster("erg", "class-1", null, rosterPage))
                .thenReturn(new PageImpl<>(roster, rosterPage, 3));
        PageRequest assignmentPage = PageRequest.of(0, 20);
        List<AssignmentCardProjection> assignments = List.of(
                new AssignmentCardProjection(
                        "assignment-1",
                        "class-1",
                        "quiz-1",
                        "math",
                        now.plusDays(1),
                        "active",
                        "teacher-1",
                        "CLASS",
                        now.minusDays(2),
                        now
                ),
                new AssignmentCardProjection(
                        "assignment-2",
                        "class-1",
                        "quiz-2",
                        "physics",
                        now.plusDays(2),
                        "active",
                        "teacher-1",
                        "CLASS",
                        now.minusDays(1),
                        now
                )
        );
        when(assignmentRepository.findClassAssignmentCards("erg", "class-1", null, assignmentPage))
                .thenReturn(new PageImpl<>(assignments, assignmentPage, 2));
        when(assignmentResourceRepository.findResourceProjectionsByAssignmentIds(
                "erg",
                List.of("assignment-1", "assignment-2")
        )).thenReturn(List.of(
                new AssignmentResourceProjection("assignment-1", "quiz-1", "version-1", "v1", 0),
                new AssignmentResourceProjection("assignment-2", "quiz-2", "version-2", "v1", 0)
        ));

        ClassWorkspaceResponse response = service.classWorkspace(
                " erg ",
                " class-1 ",
                " ",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.students().items())
                .extracting("id")
                .containsExactly("student-1", "student-2", "student-3");
        assertThat(response.assignments().items())
                .extracting("id")
                .containsExactly("assignment-1", "assignment-2");
        verify(classroomRepository, times(1)).findClassWorkspace("erg", "class-1", null);
        verify(studentRepository, times(1)).findClassRoster("erg", "class-1", null, rosterPage);
        verify(assignmentRepository, times(1)).findClassAssignmentCards("erg", "class-1", null, assignmentPage);
        verify(assignmentResourceRepository, times(1))
                .findResourceProjectionsByAssignmentIds("erg", List.of("assignment-1", "assignment-2"));
        verifyNoMoreInteractions(classroomRepository, studentRepository, assignmentRepository, assignmentResourceRepository, attemptRepository);
    }

    @Test
    void classWorkspaceIncludesScoreSummaryOnlyWhenRequested() {
        OffsetDateTime now = OffsetDateTime.now();
        ClassWorkspaceProjection classProjection = new ClassWorkspaceProjection(
                "class-1",
                "school-1",
                "10A1",
                "10",
                "2026",
                "active",
                "teacher-1",
                30L,
                4L,
                now
        );
        PageRequest rosterPage = PageRequest.of(
                0,
                20,
                Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending())
        );
        PageRequest assignmentPage = PageRequest.of(0, 20);
        when(classroomRepository.findClassWorkspace("erg", "class-1", null))
                .thenReturn(Optional.of(classProjection));
        when(studentRepository.findClassRoster("erg", "class-1", null, rosterPage))
                .thenReturn(new PageImpl<>(List.of(), rosterPage, 0));
        when(assignmentRepository.findClassAssignmentCards("erg", "class-1", null, assignmentPage))
                .thenReturn(new PageImpl<>(List.of(), assignmentPage, 0));
        when(attemptRepository.summarizeClassWorkspaceScores("erg", "class-1"))
                .thenReturn(new ClassScoreSummaryProjection(7L, 5L, 71.6D, 99.2D));

        ClassWorkspaceResponse response = service.classWorkspace(
                "erg",
                "class-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertThat(response.scoreSummary().completedAttemptCount()).isEqualTo(7);
        assertThat(response.scoreSummary().attemptedStudentCount()).isEqualTo(5);
        assertThat(response.scoreSummary().averagePercent()).isEqualTo(72);
        assertThat(response.scoreSummary().bestPercent()).isEqualTo(99);
        verify(attemptRepository, times(1)).summarizeClassWorkspaceScores("erg", "class-1");
    }

    @Test
    void classWorkspaceIncludesRiskSummaryOnlyWhenRequested() {
        OffsetDateTime now = OffsetDateTime.now();
        ClassWorkspaceProjection classProjection = new ClassWorkspaceProjection(
                "class-1",
                "school-1",
                "10A1",
                "10",
                "2026",
                "active",
                "teacher-1",
                30L,
                4L,
                now
        );
        PageRequest rosterPage = PageRequest.of(
                0,
                20,
                Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending())
        );
        PageRequest assignmentPage = PageRequest.of(0, 20);
        when(classroomRepository.findClassWorkspace("erg", "class-1", null))
                .thenReturn(Optional.of(classProjection));
        when(studentRepository.findClassRoster("erg", "class-1", null, rosterPage))
                .thenReturn(new PageImpl<>(List.of(), rosterPage, 0));
        when(assignmentRepository.findClassAssignmentCards("erg", "class-1", null, assignmentPage))
                .thenReturn(new PageImpl<>(List.of(), assignmentPage, 0));
        when(studentRepository.summarizeClassWorkspaceRisks("erg", "class-1", 50.0D))
                .thenReturn(new ClassRiskSummaryProjection(3L, 2L, 1L));

        ClassWorkspaceResponse response = service.classWorkspace(
                "erg",
                "class-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                true
        );

        assertThat(response.scoreSummary()).isNull();
        assertThat(response.riskSummary().missingOverdueStudentCount()).isEqualTo(3);
        assertThat(response.riskSummary().lowScoreStudentCount()).isEqualTo(2);
        assertThat(response.riskSummary().inactiveStudentCount()).isEqualTo(1);
        verifyNoMoreInteractions(attemptRepository);
        verify(studentRepository, times(1)).summarizeClassWorkspaceRisks("erg", "class-1", 50.0D);
    }

    @Test
    void teacherHomeworkWorkspaceUsesBoundedAssignmentProjectionPageOnly() {
        OffsetDateTime now = OffsetDateTime.now();
        PageRequest clampedPage = PageRequest.of(2, 50);
        AssignmentCardProjection assignment = new AssignmentCardProjection(
                "assignment-1",
                "class-1",
                "quiz-1",
                "math",
                now.plusDays(3),
                "active",
                "teacher-1",
                "CLASS",
                now.minusDays(1),
                now
        );
        when(assignmentRepository.findTeacherAssignmentCards("erg", "teacher-1", "active", clampedPage))
                .thenReturn(new PageImpl<>(List.of(assignment), clampedPage, 101));
        when(assignmentResourceRepository.findResourceProjectionsByAssignmentIds("erg", List.of("assignment-1")))
                .thenReturn(List.of(new AssignmentResourceProjection("assignment-1", "quiz-1", "version-1", "v1", 0)));
        when(assignmentRepository.summarizeTeacherHomeworkWorkspace("erg", "teacher-1"))
                .thenReturn(new TeacherHomeworkSummaryProjection(9L, 2L, 4L));
        when(classroomRepository.findTeacherClassOptions("erg", "teacher-1"))
                .thenReturn(List.of(new ClassOptionProjection(
                        "class-1",
                        "school-1",
                        "10A1",
                        "10",
                        "2026",
                        "active"
                )));
        when(attemptRepository.findTeacherPendingReviews("erg", "teacher-1", clampedPage))
                .thenReturn(new PageImpl<>(List.of(new PendingReviewProjection(
                        "attempt-1",
                        "assignment-1",
                        "class-1",
                        "quiz-1",
                        "math",
                        "student-1",
                        "S001",
                        "Nguyen Van A",
                        "submitted",
                        8.4D,
                        10.0D,
                        84.4D,
                        now.minusHours(2),
                        now.minusHours(1)
                )), clampedPage, 1));
        when(studentRepository.findTeacherOverdueStudents("erg", "teacher-1", clampedPage))
                .thenReturn(new PageImpl<>(List.of(new OverdueStudentProjection(
                        "assignment-1",
                        "class-1",
                        "quiz-1",
                        "math",
                        now.minusDays(1),
                        "student-2",
                        "S002",
                        "Tran Thi B",
                        "active",
                        now
                )), clampedPage, 1));

        TeacherHomeworkWorkspaceResponse response = service.teacherHomeworkWorkspace(
                " erg ",
                " teacher-1 ",
                " ACTIVE ",
                2,
                500
        );

        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.teacherId()).isEqualTo("teacher-1");
        assertThat(response.summary().activeAssignmentCount()).isEqualTo(9);
        assertThat(response.summary().overdueAssignmentCount()).isEqualTo(2);
        assertThat(response.summary().pendingReviewAssignmentCount()).isEqualTo(4);
        assertThat(response.classOptions())
                .extracting("id")
                .containsExactly("class-1");
        assertThat(response.assignments().size()).isEqualTo(50);
        assertThat(response.assignments().totalItems()).isEqualTo(101);
        assertThat(response.assignments().items())
                .extracting("id")
                .containsExactly("assignment-1");
        assertThat(response.assignments().items().getFirst().assignedBy()).isEqualTo("teacher-1");
        assertThat(response.assignments().items().getFirst().resources()).hasSize(1);
        assertThat(response.assignments().items().getFirst().resources().getFirst().quizVersionId()).isEqualTo("version-1");
        assertThat(response.pendingReviews().items()).hasSize(1);
        assertThat(response.pendingReviews().items().getFirst().attemptId()).isEqualTo("attempt-1");
        assertThat(response.pendingReviews().items().getFirst().percent()).isEqualTo(84);
        assertThat(response.overdueStudents().items()).hasSize(1);
        assertThat(response.overdueStudents().items().getFirst().studentId()).isEqualTo("student-2");
        verify(assignmentRepository, times(1))
                .findTeacherAssignmentCards("erg", "teacher-1", "active", clampedPage);
        verify(assignmentResourceRepository, times(1))
                .findResourceProjectionsByAssignmentIds("erg", List.of("assignment-1"));
        verify(assignmentRepository, times(1))
                .summarizeTeacherHomeworkWorkspace("erg", "teacher-1");
        verify(classroomRepository, times(1)).findTeacherClassOptions("erg", "teacher-1");
        verify(attemptRepository, times(1)).findTeacherPendingReviews("erg", "teacher-1", clampedPage);
        verify(studentRepository, times(1)).findTeacherOverdueStudents("erg", "teacher-1", clampedPage);
        verifyNoMoreInteractions(classroomRepository, studentRepository, assignmentRepository, assignmentResourceRepository, attemptRepository);
    }

    @Test
    void teacherHomeworkWorkspaceUsesOneAssignmentPageWithDefaultsForEmptyResults() {
        PageRequest defaultPage = PageRequest.of(0, 20);
        when(assignmentRepository.findTeacherAssignmentCards("erg", "teacher-1", null, defaultPage))
                .thenReturn(new PageImpl<>(List.of(), defaultPage, 0));
        when(assignmentRepository.summarizeTeacherHomeworkWorkspace("erg", "teacher-1"))
                .thenReturn(new TeacherHomeworkSummaryProjection(null, null, null));
        when(classroomRepository.findTeacherClassOptions("erg", "teacher-1"))
                .thenReturn(List.of());
        when(attemptRepository.findTeacherPendingReviews("erg", "teacher-1", defaultPage))
                .thenReturn(new PageImpl<>(List.of(), defaultPage, 0));
        when(studentRepository.findTeacherOverdueStudents("erg", "teacher-1", defaultPage))
                .thenReturn(new PageImpl<>(List.of(), defaultPage, 0));

        TeacherHomeworkWorkspaceResponse response = service.teacherHomeworkWorkspace(
                "erg",
                "teacher-1",
                " ",
                -1,
                0
        );

        assertThat(response.assignments().items()).isEmpty();
        assertThat(response.assignments().page()).isZero();
        assertThat(response.assignments().size()).isEqualTo(20);
        assertThat(response.assignments().totalItems()).isZero();
        assertThat(response.classOptions()).isEmpty();
        assertThat(response.pendingReviews().items()).isEmpty();
        assertThat(response.overdueStudents().items()).isEmpty();
        assertThat(response.summary().activeAssignmentCount()).isZero();
        assertThat(response.summary().overdueAssignmentCount()).isZero();
        assertThat(response.summary().pendingReviewAssignmentCount()).isZero();
        verify(assignmentRepository, times(1))
                .findTeacherAssignmentCards("erg", "teacher-1", null, defaultPage);
        verify(assignmentRepository, times(1))
                .summarizeTeacherHomeworkWorkspace("erg", "teacher-1");
        verify(classroomRepository, times(1)).findTeacherClassOptions("erg", "teacher-1");
        verify(attemptRepository, times(1)).findTeacherPendingReviews("erg", "teacher-1", defaultPage);
        verify(studentRepository, times(1)).findTeacherOverdueStudents("erg", "teacher-1", defaultPage);
        verifyNoMoreInteractions(classroomRepository, studentRepository, assignmentRepository, attemptRepository);
    }
}
