package com.erg.ergspringboot.service.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizAuthoringServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizDraftRepository quizDraftRepository;

    @Mock
    private QuizQuestionLinkService quizQuestionLinkService;

    @Mock
    private AssessmentCurriculumBridge assessmentCurriculumBridge;

    private QuizAuthoringService service;

    @BeforeEach
    void setUp() {
        service = new QuizAuthoringService(
                userRepository,
                quizRepository,
                quizDraftRepository,
                quizQuestionLinkService,
                assessmentCurriculumBridge
        );
    }

    @Test
    void createPersistsTeacherUserAsQuizOwner() {
        User teacher = new User();
        teacher.setId("teacher-1");
        teacher.setTenantId("tenant-a");
        when(userRepository.findByIdAndTenantId("teacher-1", "tenant-a")).thenReturn(Optional.of(teacher));
        when(quizRepository.findByTenantIdAndIdempotencyKey("tenant-a", "key-1")).thenReturn(Optional.empty());
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            if (quiz.getId() == null) {
                quiz.setId("quiz-1");
            }
            return quiz;
        });
        when(quizDraftRepository.save(any(QuizDraft.class))).thenAnswer(invocation -> {
            QuizDraft draft = invocation.getArgument(0);
            draft.setId("draft-1");
            return draft;
        });
        when(quizQuestionLinkService.syncManualQuestionIds("tenant-a", "quiz-1", List.of("q-1")))
                .thenReturn(List.of("q-1"));

        service.create("tenant-a", "teacher-1", "key-1", new CreateQuizRequest(
                "Quiz 1",
                null,
                "train",
                "subject-1",
                "level-1",
                "topic-1",
                List.of("q-1")
        ));

        ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository, times(2)).save(quizCaptor.capture());
        assertThat(quizCaptor.getAllValues().getFirst().getOwnerUserId()).isEqualTo("teacher-1");

        ArgumentCaptor<QuizDraft> draftCaptor = ArgumentCaptor.forClass(QuizDraft.class);
        verify(quizDraftRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getUpdatedBy()).isEqualTo("teacher-1");
        verify(assessmentCurriculumBridge).validateQuizPlacement("tenant-a", "subject-1", "level-1", "topic-1");
    }

    @Test
    void createRejectsAuthorThatDoesNotExistInUsersForTenant() {
        when(userRepository.findByIdAndTenantId("missing-teacher", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("tenant-a", "missing-teacher", "key-1", new CreateQuizRequest(
                "Quiz 1",
                null,
                "train",
                "subject-1",
                "level-1",
                "topic-1",
                List.of()
        ))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("author user not found");
    }
}
