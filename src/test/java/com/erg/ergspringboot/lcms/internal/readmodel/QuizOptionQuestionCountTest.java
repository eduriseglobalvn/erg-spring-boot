package com.erg.ergspringboot.lcms.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.projection.QuestionBankQuizOptionProjection;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizOptionQuestionCountTest {

    @Test
    void editorQuizCountsTheQuestionsOfItsPublishedVersion() {
        // Editor-authored quizzes link no bank rows, so questionIds is empty; the published version has the questions.
        var option = new QuestionBankQuizOptionProjection(
                "quiz-1", "Cộng tác", "published", "train", "s", "l", "c", List.of(), null, null, 3L);

        assertThat(LcmsWorkspaceResponseMapper.toQuizOption(option, null).questionCount()).isEqualTo(3);
    }

    @Test
    void bankQuizKeepsItsLinkedQuestionCount() {
        var option = new QuestionBankQuizOptionProjection(
                "quiz-2", "Từ ngân hàng", "draft", "train", "s", "l", "c", List.of("q1", "q2"), null, null, 0L);

        assertThat(LcmsWorkspaceResponseMapper.toQuizOption(option, null).questionCount()).isEqualTo(2);
    }
}
