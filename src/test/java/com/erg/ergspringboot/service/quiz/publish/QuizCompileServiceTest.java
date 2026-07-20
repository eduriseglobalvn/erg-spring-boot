package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.service.quiz.publish.CompiledQuiz.CompiledQuestion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Table-driven coverage of the authoring -> runtime compile (spec §3). Verifies every one of the 14
 * question types maps to the right runtime kind + grading policy, that presentation slides drop out,
 * and that correctness is split out of the player-facing body into the answer key (§6 anti-leak).
 */
class QuizCompileServiceTest {

    private final QuizCompileService service = new QuizCompileService();

    private Map<String, Object> tree(Map<String, Object>... slides) {
        return Map.of("groupId", "g1", "title", "Section 1", "slides", List.of(slides));
    }

    private CompiledQuiz compileSlides(Map<String, Object>... slides) {
        return service.compile(Map.of(), List.of(tree(slides)));
    }

    @Test
    void mapsAllFourteenAuthoringTypesToRuntimeKinds() {
        record Case(String authoringType, String expectedKind, String expectedPolicy, boolean autoGradable) {
        }
        List<Case> cases = List.of(
                new Case("multiple-choice", "single_choice", "auto", true),
                new Case("multiple-response", "multiple_response", "auto", true),
                new Case("true-false", "true_false", "auto", true),
                new Case("short-answer", "short_answer", "auto", true),
                new Case("numeric", "numeric", "auto", true),
                new Case("sequence", "sequence", "auto", true),
                new Case("matching", "matching", "auto", true),
                new Case("fill-in-the-blanks", "fill_blank", "auto", true),
                new Case("select-from-lists", "select_from_lists", "auto", true),
                new Case("drag-the-words", "drag_words", "auto", true),
                new Case("hotspot", "hotspot", "auto", true),
                new Case("drag-and-drop", "drag_drop", "auto", true),
                new Case("likert-scale", "likert_scale", "survey", false),
                new Case("essay", "essay", "manual", false)
        );

        for (Case testCase : cases) {
            CompiledQuiz compiled = compileSlides(Map.of(
                    "slideId", "s-" + testCase.authoringType(),
                    "type", testCase.authoringType()
            ));
            assertThat(compiled.questions()).hasSize(1);
            CompiledQuestion question = compiled.questions().getFirst();
            assertThat(question.kind()).as(testCase.authoringType()).isEqualTo(testCase.expectedKind());
            assertThat(question.gradingPolicy()).as(testCase.authoringType()).isEqualTo(testCase.expectedPolicy());
            assertThat(question.autoGradable()).as(testCase.authoringType()).isEqualTo(testCase.autoGradable());
        }
    }

    @Test
    void dropsPresentationSlidesFromQuestionList() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "intro", "type", "intro-slide"),
                Map.of("slideId", "info", "type", "info-slide"),
                Map.of("slideId", "q1", "type", "multiple-choice"),
                Map.of("slideId", "result", "type", "result-slide")
        );
        assertThat(compiled.questions()).hasSize(1);
        assertThat(compiled.questions().getFirst().sourceSlideId()).isEqualTo("q1");
    }

    @Test
    void splitsCorrectChoiceOutOfBodyIntoAnswerKey() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "slideId", "q1",
                "type", "multiple-choice",
                "options", List.of(
                        Map.of("id", "a", "label", "Wrong", "correct", false),
                        Map.of("id", "b", "label", "Right", "correct", true)
                )
        ));

        CompiledQuestion question = compiled.questions().getFirst();
        // Body options must NOT leak the correct flag.
        List<Map<String, Object>> bodyOptions = (List<Map<String, Object>>) question.body().get("options");
        assertThat(bodyOptions).allSatisfy(option -> assertThat(option).doesNotContainKey("correct"));
        // Answer key holds the correct choice, keyed by runtime question id.
        assertThat(question.answerKey()).containsEntry("correctChoiceId", "b");
        assertThat(compiled.answerKey()).containsKey(question.runtimeQuestionId());
    }

    @Test
    void acceptsFrontendKindChoicesAndStableQuestionId() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "id", "q-editor-1",
                "kind", "multiple-choice",
                "choices", List.of(
                        Map.of("id", "a", "label", "Right", "correct", true),
                        Map.of("id", "b", "label", "Wrong", "correct", false)
                )
        ));

        CompiledQuestion question = compiled.questions().getFirst();
        assertThat(question.runtimeQuestionId()).isEqualTo("q-editor-1");
        assertThat(question.kind()).isEqualTo("single_choice");
        assertThat(question.body()).containsKey("choices");
        assertThat(question.answerKey()).containsEntry("correctChoiceId", "a");
    }

    @Test
    void multipleResponseCollectsAllCorrectChoiceIds() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "slideId", "q1",
                "type", "multiple-response",
                "acceptPartial", true,
                "options", List.of(
                        Map.of("id", "a", "correct", true),
                        Map.of("id", "b", "correct", false),
                        Map.of("id", "c", "correct", true)
                )
        ));
        CompiledQuestion question = compiled.questions().getFirst();
        assertThat((List<String>) question.answerKey().get("correctChoiceIds")).containsExactly("a", "c");
        assertThat(question.answerKey()).containsEntry("acceptPartial", true);
    }

    @Test
    void numericKeepsValueAndToleranceInKeyOnly() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "slideId", "q1",
                "type", "numeric",
                "correctAnswer", 42,
                "tolerance", 0.5
        ));
        CompiledQuestion question = compiled.questions().getFirst();
        assertThat(question.answerKey()).containsEntry("value", 42).containsEntry("tolerance", 0.5);
        assertThat(question.body()).doesNotContainKey("tolerance").doesNotContainKey("correctAnswer");
    }

    @Test
    void essayAndLikertHaveNoAnswerKey() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "e1", "type", "essay"),
                Map.of("slideId", "l1", "type", "likert-scale")
        );
        assertThat(compiled.answerKey()).isEmpty();
    }

    @Test
    void distributesPackagePointsFromFixedOneThousandPointTotal() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "q1", "type", "true-false"),
                Map.of("slideId", "q2", "type", "multiple-choice")
        );

        assertThat(compiled.questions()).extracting(CompiledQuestion::points)
                .containsExactly(new java.math.BigDecimal("500.00"), new java.math.BigDecimal("500.00"));
    }

    @Test
    void groupsBecomeSectionsInOrder() {
        CompiledQuiz compiled = service.compile(Map.of(), List.of(
                Map.of("groupId", "g1", "title", "First", "slides", List.of(
                        Map.of("slideId", "q1", "type", "true-false"))),
                Map.of("groupId", "g2", "title", "Second", "slides", List.of(
                        Map.of("slideId", "q2", "type", "true-false")))
        ));
        assertThat(compiled.sections()).hasSize(2);
        assertThat(compiled.sections().get(0).title()).isEqualTo("First");
        assertThat(compiled.sections().get(1).title()).isEqualTo("Second");
    }

    @Test
    void dropsPresentationFieldsFromRuntimeBody() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "slideId", "q1",
                "type", "short-answer",
                "textStyles", Map.of("bold", true),
                "layoutPreset", "centered",
                "animationPreset", "fade",
                "correctAnswers", List.of("answer")
        ));
        CompiledQuestion question = compiled.questions().getFirst();
        assertThat(question.body())
                .doesNotContainKey("textStyles")
                .doesNotContainKey("layoutPreset")
                .doesNotContainKey("animationPreset")
                .doesNotContainKey("correctAnswers");
        assertThat(question.answerKey()).containsEntry("correctAnswers", List.of("answer"));
    }

    @Test
    void questionSetSettingsOverrideQuestionSettingsInRuntimeBody() {
        CompiledQuiz compiled = service.compile(
                Map.of(),
                List.of(tree(Map.of(
                        "questionId", "bank-q1",
                        "type", "multiple-choice",
                        "settings", Map.of("showFeedback", "slide"),
                        "questionSetSettings", Map.of("shuffleChoices", true, "showFeedback", "set")
                ))),
                Map.of("bank-q1", Map.of("shuffleChoices", false, "timeLimitSeconds", 30, "showFeedback", "question")),
                Map.of("bank-q1", Map.of("timeLimitSeconds", 45))
        );

        Map<String, Object> bodySettings = (Map<String, Object>) compiled.questions().getFirst().body().get("settings");
        assertThat(bodySettings)
                .containsEntry("shuffleChoices", true)
                .containsEntry("timeLimitSeconds", 45)
                .containsEntry("showFeedback", "set");
    }
}
