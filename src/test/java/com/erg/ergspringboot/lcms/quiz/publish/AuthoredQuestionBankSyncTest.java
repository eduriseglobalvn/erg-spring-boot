package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.Question;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthoredQuestionBankSyncTest {

    private final QuestionRepository repository = mock(QuestionRepository.class);
    private final AuthoredQuestionBankSync sync = new AuthoredQuestionBankSync(repository);

    @Test
    void singleChoiceIsMirroredIntoTheBankWithItsCorrectChoice() {
        when(repository.saveAndFlush(any(Question.class))).thenAnswer(call -> {
            Question saved = call.getArgument(0);
            saved.setId("bank-1");
            return saved;
        });

        String id = sync.upsert("erg", "usr-1", quiz(), single(), null);

        assertThat(id).isEqualTo("bank-1");
        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(repository).saveAndFlush(captor.capture());
        Question bank = captor.getValue();
        assertThat(bank.getType()).isEqualTo("single_choice");
        assertThat(bank.getStem()).isEqualTo("Máy tính dùng thiết bị nào để nhập chữ?");
        assertThat(bank.getSubjectId()).isEqualTo("sub-1");
        assertThat(bank.getTopicId()).isEqualTo("cat-1");
        assertThat(bank.getStatus()).isEqualTo("ready");
        assertThat(bank.getAnswer()).isEqualTo("Bàn phím");
        assertThat(bank.getChoices()).extracting("label", "correct").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Bàn phím", true), org.assertj.core.groups.Tuple.tuple("Loa", false));
    }

    @Test
    void republishUpdatesTheSameBankRowInsteadOfCreatingAnother() {
        Question existing = new Question();
        existing.setId("bank-1");
        when(repository.findByIdAndTenantId("bank-1", "erg")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Question.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(sync.upsert("erg", "usr-1", quiz(), single(), "bank-1")).isEqualTo("bank-1");
        assertThat(existing.getStem()).isEqualTo("Máy tính dùng thiết bị nào để nhập chữ?");
    }

    @Test
    void archivedBankRowIsNotResurrected() {
        Question archived = new Question();
        archived.setId("bank-1");
        archived.setArchivedAt(OffsetDateTime.now());
        when(repository.findByIdAndTenantId("bank-1", "erg")).thenReturn(Optional.of(archived));

        assertThat(sync.upsert("erg", "usr-1", quiz(), single(), "bank-1")).isEqualTo("bank-1");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void kindsWithoutAMappedBankShapeAndQuizzesWithoutPlacementAreSkipped() {
        CompiledQuestion matching = new CompiledQuestion("q", "slide-1", "s", null, "matching", "matching", BigDecimal.TEN, true, "auto", 0,
                Map.of("title", "Ghép cặp"), Map.of());
        assertThat(sync.upsert("erg", "usr-1", quiz(), matching, null)).isNull();

        Quiz unplaced = new Quiz();
        assertThat(sync.upsert("erg", "usr-1", unplaced, single(), null)).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    private Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setSubjectId("sub-1");
        quiz.setLevelId("lvl-1");
        quiz.setCategoryId("cat-1");
        return quiz;
    }

    private CompiledQuestion single() {
        return new CompiledQuestion("q-1", "slide-1", "s", null, "single_choice", "multiple-choice", BigDecimal.TEN, true, "auto", 0,
                Map.of("title", "Máy tính dùng thiết bị nào để nhập chữ?",
                        "choices", List.of(Map.of("id", "c1", "label", "Bàn phím"), Map.of("id", "c2", "label", "Loa"))),
                Map.of("correctChoiceId", "c1"));
    }
}
