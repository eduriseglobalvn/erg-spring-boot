package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.CreateAssignmentRequest;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.CreateAssignmentResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.AssignmentRecipient;
import com.erg.ergspringboot.model.lms.AssignmentResource;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentGroupMember;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.repository.lms.AssignmentRecipientRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LmsAssignmentCommandServiceTest {

    @Mock private AssignmentRepository assignmentRepository;
    @Mock private AssignmentResourceRepository resourceRepository;
    @Mock private AssignmentRecipientRepository recipientRepository;
    @Mock private StudentGroupRepository studentGroupRepository;
    @Mock private StudentGroupMemberRepository studentGroupMemberRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private QuizVersionRepository quizVersionRepository;
    @Mock private CacheManager cacheManager;
    @Mock private Cache lmsHomeworkWorkspaceCache;
    @Mock private Cache lmsClassWorkspaceCache;
    @Mock private Cache lmsAssignmentProgressCache;
    @Mock private Cache studentDashboardCache;
    @Mock private Cache quizAssignmentReportCache;
    @Mock private Cache quizQuestionAnalyticsCache;

    private LmsAssignmentCommandService service;

    @BeforeEach
    void setUp() {
        service = new LmsAssignmentCommandService(
                assignmentRepository, resourceRepository, recipientRepository,
                studentGroupRepository, studentGroupMemberRepository, studentRepository, quizVersionRepository, cacheManager);
        when(cacheManager.getCache("lmsHomeworkWorkspace")).thenReturn(lmsHomeworkWorkspaceCache);
        when(cacheManager.getCache("lmsClassWorkspace")).thenReturn(lmsClassWorkspaceCache);
        when(cacheManager.getCache("lmsAssignmentProgress")).thenReturn(lmsAssignmentProgressCache);
        when(cacheManager.getCache("studentDashboard")).thenReturn(studentDashboardCache);
        when(cacheManager.getCache("quizAssignmentReport")).thenReturn(quizAssignmentReportCache);
        when(cacheManager.getCache("quizQuestionAnalytics")).thenReturn(quizQuestionAnalyticsCache);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            if (a.getId() == null) {
                a.setId("assignment-1");
            }
            return a;
        });
        when(resourceRepository.save(any(AssignmentResource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.save(any(AssignmentRecipient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resourceRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        org.springframework.transaction.support.TransactionSynchronizationManager.clear();
    }

    private Student student(String id, String classId) {
        Student s = new Student();
        s.setId(id);
        s.setTenantId("tenant-a");
        s.setAcademicClassId(classId);
        s.setAuthUserId("user-" + id);
        return s;
    }

    private QuizVersion activeVersion(String quizId) {
        QuizVersion v = new QuizVersion();
        v.setId("ver-" + quizId);
        v.setQuizId(quizId);
        v.setTenantId("tenant-a");
        v.setVersionLabel("v1");
        v.setVersionNumber(1);
        v.setIsActive(true);
        return v;
    }

    @Test
    void createClassModePinsActiveVersionAndFlattensRecipients() {
        when(studentRepository.findByTenantIdAndAcademicClassIdOrderByFullNameAsc("tenant-a", "class-1"))
                .thenReturn(List.of(student("s1", "class-1"), student("s2", "class-1")));
        when(quizVersionRepository.findByQuizIdInAndTenantIdAndIsActiveTrue(eq(List.of("quiz-1")), eq("tenant-a")))
                .thenReturn(List.of(activeVersion("quiz-1")));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(activeVersion("quiz-1")));

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Homework 1", List.of("quiz-1"), "class", "class-1", null, null, null, null, null, null, null);

        CreateAssignmentResponse response = service.create("tenant-a", "teacher-1", "idem-1", request);

        assertThat(response.recipientCount()).isEqualTo(2);
        assertThat(response.resources()).singleElement()
                .satisfies(resource -> {
                    assertThat(resource.quizId()).isEqualTo("quiz-1");
                    assertThat(resource.quizVersionId()).isEqualTo("ver-quiz-1");
                });
        // One flattened recipient row per resolved student (spec §1.8).
        verify(recipientRepository).saveAll(anyList());

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        Assignment saved = captor.getValue();
        // Legacy columns are still filled so old read projections keep working during transition.
        assertThat(saved.getAcademicClassId()).isEqualTo("class-1");
        assertThat(saved.getQuizId()).isEqualTo("quiz-1");
        assertThat(saved.getRecipientMode()).isEqualTo("class");
        assertThat(saved.getIdempotencyKey()).isEqualTo("idem-1");
        verify(lmsHomeworkWorkspaceCache).clear();
        verify(lmsClassWorkspaceCache).clear();
        verify(lmsAssignmentProgressCache).clear();
        verify(studentDashboardCache).clear();
        verify(quizAssignmentReportCache).clear();
        verify(quizQuestionAnalyticsCache).clear();
    }

    @Test
    void createIsIdempotentOnKey() {
        Assignment existing = new Assignment();
        existing.setId("assignment-existing");
        existing.setRecipientCount(3);
        when(assignmentRepository.findByTenantIdAndIdempotencyKey("tenant-a", "idem-dup"))
                .thenReturn(Optional.of(existing));
        when(resourceRepository.findByAssignmentIdOrderByOrderIndexAsc("assignment-existing"))
                .thenReturn(List.of());

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Homework", List.of("quiz-1"), "class", "class-1", null, null, null, null, null, null, null);

        CreateAssignmentResponse response = service.create("tenant-a", "teacher-1", "idem-dup", request);

        assertThat(response.assignmentId()).isEqualTo("assignment-existing");
        assertThat(response.recipientCount()).isEqualTo(3);
        // Replay returns the existing assignment without creating a new one.
        verify(assignmentRepository, never()).save(any(Assignment.class));
        verify(lmsHomeworkWorkspaceCache, never()).clear();
        verify(lmsClassWorkspaceCache, never()).clear();
        verify(lmsAssignmentProgressCache, never()).clear();
        verify(studentDashboardCache, never()).clear();
        verify(quizAssignmentReportCache, never()).clear();
        verify(quizQuestionAnalyticsCache, never()).clear();
    }

    @Test
    void createRejectsQuizWithNoPublishedVersion() {
        when(studentRepository.findByTenantIdAndAcademicClassIdOrderByFullNameAsc("tenant-a", "class-1"))
                .thenReturn(List.of(student("s1", "class-1")));
        when(quizVersionRepository.findByQuizIdInAndTenantIdAndIsActiveTrue(eq(List.of("quiz-unpub")), eq("tenant-a")))
                .thenReturn(List.of());
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-unpub", "tenant-a"))
                .thenReturn(Optional.empty());

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Homework", List.of("quiz-unpub"), "class", "class-1", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create("tenant-a", "teacher-1", null, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no published version");
    }

    @Test
    void createGroupModeResolvesMembership() {
        StudentGroupMember m1 = new StudentGroupMember();
        m1.setGroupId("group-1");
        m1.setStudentId("s1");
        m1.setTenantId("tenant-a");
        when(studentGroupMemberRepository.findByGroupIdAndTenantId("group-1", "tenant-a"))
                .thenReturn(List.of(m1));
        when(studentRepository.findByTenantIdAndIdIn(eq("tenant-a"), any())).thenReturn(List.of(student("s1", "class-9")));
        when(quizVersionRepository.findByQuizIdInAndTenantIdAndIsActiveTrue(eq(List.of("quiz-1")), eq("tenant-a")))
                .thenReturn(List.of(activeVersion("quiz-1")));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(activeVersion("quiz-1")));

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Group HW", List.of("quiz-1"), "group", null, "group-1", null, null, null, null, null, null);

        CreateAssignmentResponse response = service.create("tenant-a", "teacher-1", null, request);

        assertThat(response.recipientCount()).isEqualTo(1);
        verify(recipientRepository).saveAll(anyList());
    }

    @Test
    void createRejectsZeroRecipients() {
        when(studentRepository.findByTenantIdAndAcademicClassIdOrderByFullNameAsc("tenant-a", "empty-class"))
                .thenReturn(List.of());

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Homework", List.of("quiz-1"), "class", "empty-class", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create("tenant-a", "teacher-1", null, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("zero recipients");
    }

    @Test
    void createRejectsMissingQuizIds() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                "Homework", List.of(), "class", "class-1", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create("tenant-a", "teacher-1", null, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("quizId");
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }
}
