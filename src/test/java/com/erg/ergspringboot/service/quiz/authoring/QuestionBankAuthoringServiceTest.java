package com.erg.ergspringboot.service.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.lms.QuestionChoice;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuestionTypeRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionBankAuthoringServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionTypeRepository questionTypeRepository;

    @Mock
    private AssessmentCurriculumBridge assessmentCurriculumBridge;

    @Test
    void createPersistsQuestionWithNormalizedAuthoringFields() {
        Question saved = new Question();
        saved.setId("question-1");
        saved.setStatus("ready");
        when(questionTypeRepository.existsById("multiple_choice")).thenReturn(true);
        when(questionRepository.save(any(Question.class))).thenReturn(saved);
        QuestionBankAuthoringService service = new QuestionBankAuthoringService(
                questionRepository,
                questionTypeRepository,
                assessmentCurriculumBridge
        );

        var response = service.create("tenant-a", "author-1", validRequest());

        assertThat(response.questionId()).isEqualTo("question-1");
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(captor.capture());
        Question question = captor.getValue();
        assertThat(question.getTenantId()).isEqualTo("tenant-a");
        assertThat(question.getOwnerUserId()).isEqualTo("author-1");
        assertThat(question.getScope().getType()).isEqualTo("global");
        assertThat(question.getSubjectId()).isEqualTo("ic3-gs6");
        assertThat(question.getType()).isEqualTo("multiple_choice");
        assertThat(question.getTags()).containsExactly("hardware");
        assertThat(question.getChoices()).hasSize(1);
        assertThat(question.getSettings()).containsEntry("shuffleChoices", false);
        verify(assessmentCurriculumBridge).validateQuestionPlacement(
                "tenant-a",
                "ic3-gs6",
                "ic3-gs6-level-1",
                "ic3-hardware"
        );
    }

    @Test
    void updateRejectsMissingStemBeforeSaving() {
        QuestionBankAuthoringService service = new QuestionBankAuthoringService(
                questionRepository,
                questionTypeRepository,
                assessmentCurriculumBridge
        );
        SaveQuestionRequest request = new SaveQuestionRequest(
                "global",
                null,
                "ic3-gs6",
                "ic3-gs6-level-1",
                "ic3-hardware",
                null,
                "multiple-choice",
                " ",
                null,
                "core",
                "ready",
                List.of(),
                List.of(),
                null,
                Map.of(),
                Map.of(),
                Map.of()
        );

        assertThatThrownBy(() -> service.update("tenant-a", "question-1", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void archiveSoftDeletesQuestion() {
        Question question = new Question();
        question.setId("question-1");
        question.setTenantId("tenant-a");
        question.setStatus("ready");
        when(questionRepository.findByIdAndTenantId("question-1", "tenant-a")).thenReturn(Optional.of(question));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuestionBankAuthoringService service = new QuestionBankAuthoringService(
                questionRepository,
                questionTypeRepository,
                assessmentCurriculumBridge
        );

        var response = service.archive("tenant-a", "question-1");

        assertThat(response.archived()).isTrue();
        assertThat(question.getStatus()).isEqualTo("archived");
        assertThat(question.getArchivedAt()).isNotNull();
        verify(questionRepository).save(question);
    }

    private SaveQuestionRequest validRequest() {
        return new SaveQuestionRequest(
                "global",
                null,
                "ic3-gs6",
                "ic3-gs6-level-1",
                "ic3-hardware",
                null,
                "multiple-choice",
                "What is a CPU?",
                "Identify hardware basics",
                "core",
                "ready",
                List.of("hardware", "hardware"),
                List.of(new QuestionChoice("a", "Processor", true)),
                "a",
                Map.of(),
                Map.of("shuffleChoices", false),
                Map.of()
        );
    }
}
