package com.erg.ergspringboot.service.lcms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.auth.user.projection.UserAccessRowProjection;
import com.erg.ergspringboot.repository.auth.user.projection.UserEffectivePermissionProjection;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.projection.RoleOptionProjection;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizQuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuestionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionBankQuizOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.QuestionUsageCountProjection;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection;
import com.erg.ergspringboot.model.lms.QuestionChoice;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class LcmsWorkspaceReadServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private CurriculumSubjectRepository subjectRepository;

    @Mock
    private CurriculumLevelRepository levelRepository;

    @Mock
    private CurriculumTopicRepository topicRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserAccessScopeRepository userAccessScopeRepository;

    private LcmsWorkspaceReadService service;

    @BeforeEach
    void setUp() {
        service = new LcmsWorkspaceReadService(
                questionRepository,
                subjectRepository,
                levelRepository,
                topicRepository,
                quizRepository,
                quizQuestionRepository,
                userRepository,
                roleRepository,
                userAccessScopeRepository
        );
    }

    @Test
    void serviceIsReadOnly() {
        Transactional transactional = LcmsWorkspaceReadService.class.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void questionBankWorkspaceMapsRepositoryProjectionsAndClampsPageSize() {
        PageRequest pageRequest = PageRequest.of(2, 50);
        when(questionRepository.findQuestionBankQuestions(
                "tenant-a",
                "algebra",
                "math",
                null,
                null,
                "active",
                pageRequest
        )).thenReturn(new PageImpl<>(
                List.of(new QuestionBankQuestionProjection(
                        "q-1",
                        null,
                        "math",
                        "Toan",
                        null,
                        null,
                        null,
                        null,
                        "Quadratic equation",
                        "Solve quadratic equations",
                        "single_choice",
                        "core",
                        "active",
                        List.of("math", "algebra"),
                        List.of(new QuestionChoice("a", "x = 1", true)),
                        "x = 1",
                        82.0,
                        2,
                        4,
                        null
                )),
                pageRequest,
                101
        ));
        when(subjectRepository.findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500)))
                .thenReturn(List.of(new QuestionBankOptionProjection("math", "Toan", null, null, "Toan")));
        when(levelRepository.findAssessmentOptions("tenant-a", "math", "active", PageRequest.of(0, 500)))
                .thenReturn(List.of(new QuestionBankOptionProjection("level-1", "Grade 10", "math", null, "Grade 10")));
        when(topicRepository.findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500)))
                .thenReturn(List.of(new QuestionBankOptionProjection("topic-1", "Polynomial", null, "level-1", "Polynomial")));
        when(quizRepository.findQuestionBankQuizOptions("tenant-a", "algebra", "math", null, null, "active", "test"))
                .thenReturn(List.of(new QuestionBankQuizOptionProjection(
                        "quiz-1",
                        "Midterm",
                        "active",
                        "test",
                        "math",
                        "level-1",
                        "topic-1",
                        List.of("q-1"),
                        "author-1",
                        null
                )));
        when(quizQuestionRepository.countUsageByQuestionIds("tenant-a", List.of("q-1")))
                .thenReturn(List.of(questionUsageCount("q-1", 3L)));

        QuestionBankWorkspaceResponse response = service.questionBankWorkspace(
                " tenant-a ",
                " algebra ",
                " math ",
                null,
                null,
                " active ",
                " test ",
                null,
                2,
                500
        );

        assertThat(response.tenantId()).isEqualTo("tenant-a");
        assertThat(response.questions().page()).isEqualTo(2);
        assertThat(response.questions().size()).isEqualTo(50);
        assertThat(response.questions().totalItems()).isEqualTo(101);
        assertThat(response.questions().items()).hasSize(1);
        assertThat(response.questions().items().getFirst().title()).isEqualTo("Quadratic equation");
        assertThat(response.questions().items().getFirst().objective()).isEqualTo("Solve quadratic equations");
        assertThat(response.questions().items().getFirst().difficulty()).isEqualTo("core");
        assertThat(response.questions().items().getFirst().tags()).containsExactly("math", "algebra");
        assertThat(response.questions().items().getFirst().choices()).containsExactly(
                new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankChoiceResponse("a", "x = 1", true)
        );
        assertThat(response.questions().items().getFirst().answer()).isEqualTo("x = 1");
        assertThat(response.questions().items().getFirst().masteryRate()).isEqualTo(82.0);
        assertThat(response.questions().items().getFirst().usageCount()).isEqualTo(3L);
        assertThat(response.questions().items().getFirst().schoolsUsing()).isEqualTo(4);
        assertThat(response.questions().items().getFirst().scopeType()).isEqualTo("global");
        assertThat(response.questions().items().getFirst().scopeLabel()).isEqualTo("Toan cong ty");
        assertThat(response.questions().items().getFirst().subjectLabel()).isEqualTo("Toan");
        assertThat(response.questions().items().getFirst().gradeLabel()).isEqualTo("Toan");
        assertThat(response.questions().items().getFirst().recommendedCluster()).isEqualTo("ERG");
        assertThat(response.subjects()).containsExactly(new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse("math", "Toan", null, null, "Toan", "Toan cong ty", List.of()));
        assertThat(response.levels()).containsExactly(new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse("level-1", "Grade 10", "math", null, "Grade 10", "Toan cong ty", List.of("topic-1")));
        assertThat(response.topics()).containsExactly(new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse("topic-1", "Polynomial", null, "level-1", "Polynomial", "Toan cong ty", List.of()));
        assertThat(response.quizzes()).containsExactly(new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuizOptionResponse(
                "quiz-1",
                "Midterm",
                "active",
                "test",
                "math",
                "level-1",
                "topic-1",
                List.of("q-1"),
                1,
                "5 phut",
                "Toan cong ty",
                "manual",
                "author-1",
                null
        ));

        verify(questionRepository).findQuestionBankQuestions("tenant-a", "algebra", "math", null, null, "active", pageRequest);
        verify(subjectRepository).findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500));
        verify(levelRepository).findAssessmentOptions("tenant-a", "math", "active", PageRequest.of(0, 500));
        verify(topicRepository).findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500));
        verify(quizRepository).findQuestionBankQuizOptions("tenant-a", "algebra", "math", null, null, "active", "test");
        verify(quizQuestionRepository).countUsageByQuestionIds("tenant-a", List.of("q-1"));
        verifyNoMoreInteractions(questionRepository, subjectRepository, levelRepository, topicRepository, quizRepository, quizQuestionRepository);
    }

    @Test
    void questionBankWorkspaceSkipsUsageCountBatchWhenQuestionPageIsEmpty() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(questionRepository.findQuestionBankQuestionsWithoutSearch(
                "tenant-a",
                null,
                null,
                null,
                null,
                pageRequest
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(subjectRepository.findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(levelRepository.findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(topicRepository.findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(quizRepository.findQuestionBankQuizOptionsWithoutStatusAndSearch("tenant-a", null, null, null, null)).thenReturn(List.of());

        QuestionBankWorkspaceResponse response = service.questionBankWorkspace(
                "tenant-a",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(response.questions().items()).isEmpty();
        assertThat(response.questions().page()).isZero();
        assertThat(response.questions().size()).isEqualTo(20);
        verify(questionRepository).findQuestionBankQuestionsWithoutSearch("tenant-a", null, null, null, null, pageRequest);
        verify(subjectRepository).findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500));
        verify(levelRepository).findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500));
        verify(topicRepository).findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500));
        verify(quizRepository).findQuestionBankQuizOptionsWithoutStatusAndSearch("tenant-a", null, null, null, null);
        verifyNoMoreInteractions(questionRepository, subjectRepository, levelRepository, topicRepository, quizRepository, quizQuestionRepository);
    }

    @Test
    void questionBankWorkspaceCanSkipQuestionPageForQuizBankReads() {
        when(subjectRepository.findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(levelRepository.findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(topicRepository.findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500))).thenReturn(List.of());
        when(quizRepository.findQuestionBankQuizOptionsWithoutStatusAndSearch("tenant-a", null, null, null, "train"))
                .thenReturn(List.of(new QuestionBankQuizOptionProjection(
                        "quiz-1",
                        "Training quiz",
                        "draft",
                        "train",
                        "python",
                        "python-foundation",
                        "python-topic-1",
                        List.of("q-1", "q-2"),
                        "author-1",
                        null
                )));

        QuestionBankWorkspaceResponse response = service.questionBankWorkspace(
                "tenant-a",
                null,
                null,
                null,
                null,
                null,
                "train",
                false,
                0,
                50
        );

        assertThat(response.questions().items()).isEmpty();
        assertThat(response.questions().totalItems()).isZero();
        assertThat(response.quizzes()).hasSize(1);

        verify(subjectRepository).findAssessmentOptions("tenant-a", "active", PageRequest.of(0, 500));
        verify(levelRepository).findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500));
        verify(topicRepository).findAssessmentOptions("tenant-a", null, "active", PageRequest.of(0, 500));
        verify(quizRepository).findQuestionBankQuizOptionsWithoutStatusAndSearch("tenant-a", null, null, null, "train");
        verifyNoMoreInteractions(questionRepository, subjectRepository, levelRepository, topicRepository, quizRepository, quizQuestionRepository);
    }

    @Test
    void accessManagementWorkspaceMapsUsersOptionsAndEffectivePermissionsWhenEnabled() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(userRepository.findAccessManagementRows("tenant-a", "vuong", "teacher", "active", pageRequest))
                .thenReturn(new PageImpl<>(
                        List.of(new UserAccessRowProjection(
                                "user-1",
                                "Vuong Tran",
                                "vuong@example.com",
                                "active",
                                2L,
                                1L,
                                OffsetDateTime.parse("2026-06-09T09:30:00Z")
                        )),
                        pageRequest,
                        1
                ));
        when(userRepository.findRoleEffectivePermissionsByUserIds(List.of("user-1")))
                .thenReturn(List.of(new UserEffectivePermissionProjection("user-1", "lms.scope.read", "role:teacher")));
        when(userRepository.findOverrideEffectivePermissionsByUserIds(List.of("user-1")))
                .thenReturn(List.of(new UserEffectivePermissionProjection("user-1", "rbac.permission.read", "override:grant")));
        when(roleRepository.findAccessManagementRoleOptions(PageRequest.of(0, 500)))
                .thenReturn(List.of(new RoleOptionProjection("role-1", "teacher")));
        when(userAccessScopeRepository.findAccessManagementScopeOptions("tenant-a", PageRequest.of(0, 500)))
                .thenReturn(List.of(new ScopeOptionProjection("unit-1", "Primary School", "school")));

        AccessManagementWorkspaceResponse response = service.accessManagementWorkspace(
                "tenant-a",
                "vuong",
                "teacher",
                "active",
                0,
                20,
                true
        );

        assertThat(response.tenantId()).isEqualTo("tenant-a");
        assertThat(response.users().items()).hasSize(1);
        assertThat(response.users().items().getFirst().userId()).isEqualTo("user-1");
        assertThat(response.users().items().getFirst().effectivePermissions()).containsExactly(
                new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.EffectivePermissionResponse("lms.scope.read", "role:teacher"),
                new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.EffectivePermissionResponse("rbac.permission.read", "override:grant")
        );
        assertThat(response.roleOptions()).containsExactly(
                new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.RoleOptionResponse("role-1", "teacher")
        );
        assertThat(response.scopeOptions()).containsExactly(
                new com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.ScopeOptionResponse("unit-1", "Primary School", "school")
        );
        assertThat(response.effectivePermissionsIncluded()).isTrue();

        verify(userRepository).findAccessManagementRows("tenant-a", "vuong", "teacher", "active", pageRequest);
        verify(userRepository).findRoleEffectivePermissionsByUserIds(List.of("user-1"));
        verify(userRepository).findOverrideEffectivePermissionsByUserIds(List.of("user-1"));
        verify(roleRepository).findAccessManagementRoleOptions(PageRequest.of(0, 500));
        verify(userAccessScopeRepository).findAccessManagementScopeOptions("tenant-a", PageRequest.of(0, 500));
        verifyNoMoreInteractions(userRepository, roleRepository, userAccessScopeRepository);
    }

    @Test
    void accessManagementWorkspaceOmitsEffectivePermissionsWhenDisabled() {
        PageRequest pageRequest = PageRequest.of(1, 50);
        when(userRepository.findAccessManagementRows("tenant-a", null, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(roleRepository.findAccessManagementRoleOptions(PageRequest.of(0, 500))).thenReturn(List.of());
        when(userAccessScopeRepository.findAccessManagementScopeOptions("tenant-a", PageRequest.of(0, 500))).thenReturn(List.of());

        AccessManagementWorkspaceResponse response = service.accessManagementWorkspace(
                "tenant-a",
                null,
                null,
                null,
                1,
                500,
                false
        );

        assertThat(response.users().size()).isEqualTo(50);
        assertThat(response.effectivePermissionsIncluded()).isFalse();
        assertThat(response.users().items()).isEmpty();
        verify(userRepository).findAccessManagementRows("tenant-a", null, null, null, pageRequest);
        verify(roleRepository).findAccessManagementRoleOptions(PageRequest.of(0, 500));
        verify(userAccessScopeRepository).findAccessManagementScopeOptions("tenant-a", PageRequest.of(0, 500));
        verifyNoMoreInteractions(userRepository, roleRepository, userAccessScopeRepository);
    }

    private QuestionUsageCountProjection questionUsageCount(String questionId, Long usageCount) {
        return new QuestionUsageCountProjection() {
            @Override
            public String getQuestionId() {
                return questionId;
            }

            @Override
            public Long getUsageCount() {
                return usageCount;
            }
        };
    }
}

