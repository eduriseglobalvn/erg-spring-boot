package com.erg.ergspringboot.service.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SaveDraftRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.SaveDraftResponse;
import com.erg.ergspringboot.exception.StaleDraftException;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizDraftServiceTest {

    @Mock
    private QuizDraftRepository quizDraftRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionLinkService quizQuestionLinkService;

    private QuizDraftService service;

    @BeforeEach
    void setUp() {
        service = new QuizDraftService(quizDraftRepository, quizRepository, quizQuestionLinkService);
    }

    private QuizDraft draftAtVersion(long docVersion) {
        QuizDraft draft = new QuizDraft();
        draft.setId("draft-1");
        draft.setQuizId("quiz-1");
        draft.setTenantId("tenant-a");
        draft.setDocVersion(docVersion);
        return draft;
    }

    private void stubLatestDraft(QuizDraft draft) {
        when(quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(draft));
        when(quizDraftRepository.save(any(QuizDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void saveDraftWithoutDocVersionIsLastWriteWins() {
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        stubLatestDraft(draftAtVersion(5L));
        when(quizQuestionLinkService.syncManualQuestionLinks(any(), any(), anyList()))
                .thenReturn(List.of("q-1", "q-2"));
        when(quizRepository.findByIdAndTenantId("quiz-1", "tenant-a")).thenReturn(Optional.of(quiz));

        SaveDraftRequest request = new SaveDraftRequest(null, Map.of("k", "v"), null, List.of(Map.of(
                "slides", List.of(Map.of("questionId", "q-1"), Map.of("questionId", "q-2"), Map.of("questionId", "q-1"))
        )));
        SaveDraftResponse response = service.saveDraft("tenant-a", "quiz-1", request);

        // Applies over current state and bumps to 6 regardless of any client version.
        assertThat(response.docVersion()).isEqualTo(6L);
        assertThat(quiz.getQuestionIds()).containsExactly("q-1", "q-2");
        verify(quizRepository).save(quiz);
    }

    @Test
    void saveDraftWithMatchingDocVersionSucceeds() {
        stubLatestDraft(draftAtVersion(5L));

        SaveDraftRequest request = new SaveDraftRequest(5L, Map.of("k", "v"), null, null);
        SaveDraftResponse response = service.saveDraft("tenant-a", "quiz-1", request);

        assertThat(response.docVersion()).isEqualTo(6L);
    }

    @Test
    void saveDraftWithStaleDocVersionIsRejectedWhenClientOptsIn() {
        when(quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc("quiz-1", "tenant-a"))
                .thenReturn(Optional.of(draftAtVersion(5L)));

        SaveDraftRequest request = new SaveDraftRequest(3L, Map.of("k", "v"), null, null);

        assertThatThrownBy(() -> service.saveDraft("tenant-a", "quiz-1", request))
                .isInstanceOf(StaleDraftException.class);
    }
}
