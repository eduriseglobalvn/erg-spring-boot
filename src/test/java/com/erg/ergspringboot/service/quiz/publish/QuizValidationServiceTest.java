package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.service.quiz.publish.QuizValidationService.ValidationError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Publish-gate rules (spec §8). Drives the validator through the compiler so the two stay in lockstep:
 * the compiler produces the answer-key shape and the validator checks it.
 */
class QuizValidationServiceTest {

    private final QuizCompileService compileService = new QuizCompileService();
    private final QuizValidationService validationService = new QuizValidationService();

    private List<ValidationError> validate(Map<String, Object>... slides) {
        CompiledQuiz compiled = compileService.compile(Map.of(),
                List.of(Map.of("groupId", "g1", "title", "S", "slides", List.of(slides))));
        return validationService.validate(compiled);
    }

    @Test
    void passesWhenAutoGradableQuestionHasAnswerKey() {
        List<ValidationError> errors = validate(Map.of(
                "slideId", "q1",
                "type", "multiple-choice",
                "options", List.of(
                        Map.of("id", "a", "correct", true),
                        Map.of("id", "b", "correct", false))
        ));
        assertThat(errors).isEmpty();
    }

    @Test
    void failsWhenNoAutoGradableQuestion() {
        List<ValidationError> errors = validate(Map.of("slideId", "e1", "type", "essay"));
        assertThat(errors).extracting(ValidationError::code).contains("NO_GRADABLE_QUESTION");
    }

    @Test
    void failsWhenSingleChoiceHasNoCorrectOption() {
        List<ValidationError> errors = validate(Map.of(
                "slideId", "q1",
                "type", "multiple-choice",
                "options", List.of(
                        Map.of("id", "a", "correct", false),
                        Map.of("id", "b", "correct", false))
        ));
        assertThat(errors).extracting(ValidationError::code).contains("MISSING_ANSWER_KEY");
    }

    @Test
    void allowsEssayWithoutPerQuestionPoints() {
        List<ValidationError> errors = validate(
                Map.of("slideId", "q1", "type", "true-false",
                        "options", List.of(Map.of("id", "t", "correct", true))),
                Map.of("slideId", "e1", "type", "essay")
        );
        assertThat(errors).isEmpty();
    }

    @Test
    void allowsLikertSurveyWithoutAnswerKey() {
        List<ValidationError> errors = validate(
                Map.of("slideId", "q1", "type", "true-false",
                        "options", List.of(Map.of("id", "t", "correct", true))),
                Map.of("slideId", "l1", "type", "likert-scale")
        );
        assertThat(errors).isEmpty();
    }
}
