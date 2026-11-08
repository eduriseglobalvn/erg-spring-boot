package com.erg.ergspringboot.lcms.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.UpdateQuizRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.UpdateQuizResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.lcms.internal.quiz.assessment.AssessmentCurriculumBridge;
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

    @Test
    void updateRenamesAndChangesKind() {
        Quiz quiz = existingQuiz();
        when(quizRepository.findByIdAndTenantId("quiz-1", "tenant-a")).thenReturn(Optional.of(quiz));
        when(quizRepository.saveAndFlush(quiz)).thenReturn(quiz);

        UpdateQuizResponse response = service.update("tenant-a", "quiz-1", new UpdateQuizRequest("  New title  ", "TEST"));

        assertThat(response.quizId()).isEqualTo("quiz-1");
        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.kind()).isEqualTo("test");
        assertThat(quiz.getTitle()).isEqualTo("New title");
    }

    @Test
    void updateKeepsTitleWhenOnlyKindIsSent() {
        Quiz quiz = existingQuiz();
        when(quizRepository.findByIdAndTenantId("quiz-1", "tenant-a")).thenReturn(Optional.of(quiz));
        when(quizRepository.saveAndFlush(quiz)).thenReturn(quiz);

        UpdateQuizResponse response = service.update("tenant-a", "quiz-1", new UpdateQuizRequest(null, "test"));

        assertThat(response.title()).isEqualTo("Old title");
        assertThat(response.kind()).isEqualTo("test");
    }

    @Test
    void updateRejectsInvalidInputWithBadRequest() {
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest("   ", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest("x".repeat(256), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest(null, "exam")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest(null, " ")))
                .isInstanceOf(BadRequestException.class);
        verify(quizRepository, never()).saveAndFlush(any(Quiz.class));
    }

    @Test
    void updateUnknownOrDeletedQuizIsNotFound() {
        when(quizRepository.findByIdAndTenantId("missing", "tenant-a")).thenReturn(Optional.empty());
        Quiz deleted = existingQuiz();
        deleted.setDeletedAt(java.time.OffsetDateTime.now());
        when(quizRepository.findByIdAndTenantId("quiz-1", "tenant-a")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update("tenant-a", "missing", new UpdateQuizRequest("A", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update("tenant-a", "quiz-1", new UpdateQuizRequest("A", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(quizRepository, never()).saveAndFlush(any(Quiz.class));
    }

    private Quiz existingQuiz() {
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTenantId("tenant-a");
        quiz.setTitle("Old title");
        quiz.setKind("train");
        return quiz;
    }
}
