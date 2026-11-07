package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QuizPackageAssemblerTest {

    private static final Map<String, Object> FEEDBACK = Map.of("correct", "Right, it is the keyboard.", "partial", "", "incorrect", "No: the keyboard.");

    private final QuizPackageAssembler assembler = new QuizPackageAssembler(mock(QuizPackageSigner.class), mock(ObjectMapper.class));

    private static CompiledQuiz compiled() {
        Map<String, Object> question = Map.of("id", "q1", "kind", "single_choice", "feedback", FEEDBACK);
        Map<String, Object> runtimeQuiz = Map.of("info", Map.of("title", "Quiz"), "sections", List.of(Map.of("id", "s1", "questions", List.of(question))));
        return new CompiledQuiz(List.of(), List.of(), runtimeQuiz, Map.of("q1", Map.of("correctChoiceIds", List.of("a"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstQuestion(Map<String, Object> payload) {
        Map<String, Object> quiz = (Map<String, Object>) payload.get("quiz");
        Map<String, Object> section = ((List<Map<String, Object>>) quiz.get("sections")).get(0);
        return ((List<Map<String, Object>>) section.get("questions")).get(0);
    }

    @Test
    void gradedPackagesCarryNoFeedbackTextAndNoAnswerKey() {
        CompiledQuiz compiled = compiled();

        Map<String, Object> payload = assembler.packagePayload("quiz-1", "v1", compiled, "server-authoritative");

        assertThat(firstQuestion(payload).get("feedback")).isEqualTo(Map.of("correct", "", "partial", "", "incorrect", ""));
        assertThat(payload).doesNotContainKey("answerKey");
        assertThat(firstQuestion(Map.of("quiz", compiled.runtimeQuiz())).get("feedback")).isEqualTo(FEEDBACK);
    }

    @Test
    void trainingPackagesKeepTheirFeedbackForTheCheckStep() {
        Map<String, Object> payload = assembler.packagePayload("quiz-1", "v1", compiled(), "client-first");

        assertThat(firstQuestion(payload).get("feedback")).isEqualTo(FEEDBACK);
        assertThat(payload).containsKey("answerKey");
    }
}
