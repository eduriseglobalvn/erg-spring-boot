package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.lms.internal.quiz.runtime.GradingEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    @SuppressWarnings("unchecked")
    void usesPerQuestionPointsFromQuestionSetSettings() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "q1", "type", "true-false", "questionSetSettings", Map.of("points", 4)),
                Map.of("slideId", "q2", "type", "multiple-choice", "questionSetSettings", Map.of("points", "6.5"))
        );

        assertThat(compiled.questions()).extracting(CompiledQuestion::points)
                .containsExactly(new BigDecimal("4.00"), new BigDecimal("6.50"));
        List<Map<String, Object>> sections = (List<Map<String, Object>>) compiled.runtimeQuiz().get("sections");
        List<Map<String, Object>> runtimeQuestions = (List<Map<String, Object>>) sections.getFirst().get("questions");
        assertThat(runtimeQuestions).extracting(q -> q.get("points"))
                .containsExactly(new BigDecimal("4.00"), new BigDecimal("6.50"));
    }

    @Test
    void questionsWithoutValidPointsSplitWhatIsLeftOfTheThousand() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "q1", "type", "true-false", "questionSetSettings", Map.of("points", 400)),
                Map.of("slideId", "q2", "type", "true-false"),
                Map.of("slideId", "q3", "type", "true-false", "questionSetSettings", Map.of("points", "abc")),
                Map.of("slideId", "q4", "type", "true-false", "questionSetSettings", Map.of("points", -5))
        );
        assertThat(compiled.questions()).extracting(CompiledQuestion::points).containsExactly(
                new BigDecimal("400.00"), new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("200.00"));

        CompiledQuiz overBudget = compileSlides(
                Map.of("slideId", "q1", "type", "true-false", "questionSetSettings", Map.of("points", 1200)),
                Map.of("slideId", "q2", "type", "true-false")
        );
        assertThat(overBudget.questions()).extracting(CompiledQuestion::points)
                .containsExactly(new BigDecimal("1200.00"), new BigDecimal("0.00"));
    }

    @Test
    void multipleResponseReadsAcceptPartialFromEditorOptions() {
        CompiledQuiz compiled = compileSlides(
                Map.of("slideId", "q1", "type", "multiple-response",
                        "options", Map.of("acceptPartial", true),
                        "choices", List.of(Map.of("id", "a", "correct", true), Map.of("id", "b", "correct", true),
                                Map.of("id", "c", "correct", false))),
                Map.of("slideId", "q2", "type", "multiple-response",
                        "choices", List.of(Map.of("id", "a", "correct", true), Map.of("id", "b", "correct", false)))
        );
        assertThat(compiled.questions().get(0).answerKey()).containsEntry("acceptPartial", true);
        assertThat(compiled.questions().get(1).answerKey()).containsEntry("acceptPartial", false);
        assertThat(new GradingEngine().grade("multiple_response", Map.of("choiceIds", List.of("a")),
                compiled.questions().get(0).answerKey(), BigDecimal.TEN).awardedPoints()).isEqualByComparingTo("5");
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

    @Test
    void flattensRuntimeQuestionAndMapsEditorMediaIntoPortableRuntimeFields() {
        CompiledQuiz compiled = compileSlides(Map.of(
                "slideId", "q-media",
                "type", "matching",
                "runtimeQuestion", Map.of(
                        "title", "Match the images",
                        "matching", List.of(Map.of(
                                "id", "pair-1",
                                "prompt", "Prompt",
                                "response", "Response",
                                "responseImage", Map.of("url", "data:image/png;base64,aW1hZ2U=")))),
                "media", Map.of(
                        "type", "image",
                        "src", "data:image/png;base64,cXVlc3Rpb24=",
                        "alt", "Question illustration")
        ));

        Map<String, Object> body = compiled.questions().getFirst().body();
        assertThat(body).containsKey("matching").containsKey("contentImage");
        assertThat(body).doesNotContainKeys("runtimeQuestion", "media");
        @SuppressWarnings("unchecked")
        Map<String, Object> contentImage = (Map<String, Object>) body.get("contentImage");
        assertThat(contentImage).containsEntry("url", "data:image/png;base64,cXVlc3Rpb24=")
                .containsEntry("alt", "Question illustration");
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Object items) {
        List<String> ids = new ArrayList<>();
        for (Object item : (List<Object>) items) {
            ids.add(((Map<String, Object>) item).get("id").toString());
        }
        return ids;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sequencePlayerBodyIsShuffledAndTheKeyKeepsTheAuthoringOrder() {
        List<Object> items = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            items.add(Map.of("id", "s" + i, "label", "Step " + i));
        }
        for (int run = 0; run < 20; run++) {
            CompiledQuestion q = compileSlides(Map.of("slideId", "q-seq", "type", "sequence",
                    "sequenceItems", items)).questions().getFirst();
            assertThat(q.answerKey().get("order")).isEqualTo(List.of("s1", "s2", "s3", "s4", "s5", "s6"));
            assertThat(ids(q.body().get("sequenceItems"))).isNotEqualTo(q.answerKey().get("order"))
                    .containsExactlyInAnyOrder("s1", "s2", "s3", "s4", "s5", "s6");
            GradingEngine engine = new GradingEngine();
            assertThat(engine.grade("sequence", Map.of("sequenceOrder", q.answerKey().get("order")), q.answerKey(),
                    BigDecimal.TEN).correct()).isTrue();
            assertThat(engine.grade("sequence", Map.of("order", ids(q.body().get("sequenceItems"))), q.answerKey(),
                    BigDecimal.TEN).correct()).isFalse();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingPlayerBodySplitsPromptsFromOpaqueShuffledResponsesAndGradesWithThoseIds() {
        CompiledQuestion q = compileSlides(Map.of("slideId", "q-match", "type", "matching", "matching", List.of(
                Map.of("id", "m1", "prompt", "Cat", "response", "Meow"),
                Map.of("id", "m2", "prompt", "Dog", "response", "Woof"),
                Map.of("id", "m3", "prompt", "Cow", "response", "Moo", "responseImage", Map.of("url", "u"))
        ))).questions().getFirst();

        List<Map<String, Object>> prompts = (List<Map<String, Object>>) q.body().get("matching");
        List<Map<String, Object>> responses = (List<Map<String, Object>>) q.body().get("matchingResponses");
        assertThat(prompts).allSatisfy(p -> assertThat(p).doesNotContainKeys("response", "responseImage"));
        assertThat(prompts).extracting(p -> p.get("prompt")).containsExactly("Cat", "Dog", "Cow");
        assertThat(responses).hasSize(3).allSatisfy(r -> assertThat(r.get("id").toString()).doesNotMatch("m[123]"));
        assertThat(q.body().toString()).doesNotContain("correctPairs");

        String meow = idOfResponse(responses, "Meow");
        String woof = idOfResponse(responses, "Woof");
        String moo = idOfResponse(responses, "Moo");
        GradingEngine engine = new GradingEngine();
        Map<String, Object> key = q.answerKey();
        assertThat(engine.grade("matching", Map.of("matchingAssignments", Map.of("m1", meow, "m2", woof, "m3", moo)),
                key, BigDecimal.TEN).correct()).isTrue();
        assertThat(engine.grade("matching", Map.of("pairs", Map.of("m1", meow, "m2", woof, "m3", moo)),
                key, BigDecimal.TEN).correct()).isTrue();
        // FE row-order answer: response ids listed in prompt order
        assertThat(engine.grade("matching", Map.of("matchingOrder", List.of(meow, woof, moo)),
                key, BigDecimal.TEN).correct()).isTrue();
        // partial credit: 1 of 3 right
        var partial = engine.grade("matching", Map.of("matchingAssignments", Map.of("m1", meow, "m2", moo, "m3", woof)),
                key, BigDecimal.TEN);
        assertThat(partial.correct()).isFalse();
        assertThat(partial.partiallyRight()).isTrue();
        assertThat(partial.awardedPoints()).isEqualByComparingTo("3.33");
        // old-style ids no longer grade as correct
        assertThat(engine.grade("matching", Map.of("matchingAssignments", Map.of("m1", "m1", "m2", "m2", "m3", "m3")),
                key, BigDecimal.TEN).awardedPoints()).isEqualByComparingTo("0");
    }

    private static String idOfResponse(List<Map<String, Object>> responses, String text) {
        return responses.stream().filter(r -> text.equals(r.get("response"))).findFirst().orElseThrow().get("id").toString();
    }
}
