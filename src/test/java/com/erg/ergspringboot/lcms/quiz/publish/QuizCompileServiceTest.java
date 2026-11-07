package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.lms.internal.quiz.runtime.GradingEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
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
        // nothing connected: the shuffled start order sent alongside is not an answer
        assertThat(engine.grade("matching", Map.of("matchingAssignments", Map.of(), "matchingOrder", List.of(meow, woof, moo)),
                key, BigDecimal.TEN).awardedPoints()).isEqualByComparingTo("0");
    }

    @Test
    void hotspotEllipseCountsOnlyInsideTheEllipse() {
        Map<String, Object> ellipse = Map.of("shape", "ellipse", "x", 0, "y", 0, "width", 1, "height", 1);
        assertThat(AnswerGrading.pointInRegion(ellipse, 0.5, 0.5)).isTrue();
        assertThat(AnswerGrading.pointInRegion(ellipse, 0.05, 0.05)).isFalse();
        assertThat(AnswerGrading.pointInRegion(Map.of("x", 0, "y", 0, "width", 1, "height", 1), 0.05, 0.05)).isTrue();
    }

    private static String idOfResponse(List<Map<String, Object>> responses, String text) {
        return responses.stream().filter(r -> text.equals(r.get("response"))).findFirst().orElseThrow().get("id").toString();
    }

    // Answer-bearing field names that must never appear at any depth of a player body.
    private static final List<String> ANSWER_KEYS = List.of(
            "correct", "isCorrect", "correctChoiceId", "correctChoiceIds", "correctAnswers", "correctAnswer",
            "correctValue", "tolerance", "correctOrder", "sequenceCorrectOrder", "correctPairs", "matchingKey",
            "correctOptionId", "correctWordId", "correctRegions", "correctTargetId", "correctTargets",
            "target", "targetMedia", "emoji", "answerKey");

    private static Map<String, Object> choice(String id, String label, boolean correct) {
        return Map.of("id", id, "label", label, "correct", correct);
    }

    private static Map<String, Object> editorSlide(String type, Map<String, Object> runtimeQuestion, Object... extra) {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("slideId", "q-" + type);
        slide.put("type", type);
        slide.put("title", "Question");
        slide.put("runtimeQuestion", runtimeQuestion);
        for (int i = 0; i < extra.length; i += 2) {
            slide.put((String) extra[i], extra[i + 1]);
        }
        return slide;
    }

    private static void collectKeys(Object value, Set<String> keys) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                keys.add(entry.getKey().toString());
                collectKeys(entry.getValue(), keys);
            }
        } else if (value instanceof List<?> list) {
            list.forEach(item -> collectKeys(item, keys));
        }
    }

    /** The editor slide (choices, dragDropItems with target/emoji) sits beside the runtime question; neither may leak. */
    @Test
    void playerBodyOfEveryKindCarriesNoAnswerDataFromTheEditorOrRuntimeShape() {
        record Case(Map<String, Object> slide, List<String> forbiddenKeys, List<String> forbiddenValues) {
        }
        List<Map<String, Object>> choices = List.of(choice("c1", "Paris", true), choice("c2", "Rome", false));
        List<Map<String, Object>> steps = List.of(choice("s1", "Step A", true), choice("s2", "Step B", true),
                choice("s3", "Step C", true));
        List<Map<String, Object>> areas = List.of(Map.of("id", "h1", "shape", "rect", "x", 1, "y", 2,
                "width", 3, "height", 4, "correct", true));
        List<Case> cases = List.of(
                new Case(editorSlide("multiple-choice", Map.of("kind", "single_choice", "choices", choices),
                        "choices", choices), List.of(), List.of()),
                new Case(editorSlide("true-false", Map.of("kind", "true_false", "choices", choices),
                        "choices", choices), List.of(), List.of()),
                new Case(editorSlide("multiple-response", Map.of("kind", "multiple_response", "choices", choices),
                        "choices", choices), List.of(), List.of()),
                new Case(editorSlide("short-answer", Map.of("kind", "short_answer", "textBlanks", List.of(
                                Map.of("id", "a1", "label", "Answer", "correctAnswers", List.of("Ha Noi secret")))),
                        "choices", List.of(choice("a1", "Ha Noi secret", true))),
                        List.of("choices"), List.of("Ha Noi secret")),
                new Case(editorSlide("numeric", Map.of("kind", "numeric",
                                "numericAnswer", Map.of("correctValue", 7351, "tolerance", 0, "unit", "kg")),
                        "choices", List.of(choice("n1", "7351", true))),
                        List.of("choices"), List.of("7351")),
                new Case(editorSlide("sequence", Map.of("kind", "sequence", "sequenceItems", List.of(
                                Map.of("id", "s1", "label", "Step A"), Map.of("id", "s2", "label", "Step B"),
                                Map.of("id", "s3", "label", "Step C")),
                                "sequenceCorrectOrder", List.of("s1", "s2", "s3")),
                        "choices", steps), List.of("choices", "order"), List.of()),
                new Case(editorSlide("matching", Map.of("kind", "matching", "matchingKey", Map.of("m1", "m1"),
                                "matching", List.of(Map.of("id", "m1", "prompt", "Cat", "response", "Meow"),
                                        Map.of("id", "m2", "prompt", "Dog", "response", "Woof"))),
                        "choices", List.of(),
                        "dragDropItems", List.of(
                                Map.of("id", "m1", "label", "Meow", "emoji", "", "target", "Cat"),
                                Map.of("id", "m2", "label", "Woof", "emoji", "", "target", "Dog"))),
                        List.of("choices", "dragDropItems", "pairs"), List.of()),
                new Case(editorSlide("fill-in-the-blanks", Map.of("kind", "fill_blank", "textBlanks", List.of(
                                Map.of("id", "b1", "label", "Blank 1", "correctAnswers", List.of("chlorophyll")))),
                        "choices", List.of(choice("b1", "chlorophyll", true))),
                        List.of("choices"), List.of("chlorophyll")),
                new Case(editorSlide("select-from-lists", Map.of("kind", "select_from_lists", "inlineBlanks", List.of(
                                Map.of("id", "i1", "statement", "Sky is green", "options", List.of("yes", "no"),
                                        "correctOptionId", "no"))),
                        "choices", List.of(choice("i1", "Sky is green", false))),
                        List.of("choices"), List.of()),
                new Case(editorSlide("drag-the-words", Map.of("kind", "drag_words",
                                "wordBank", List.of(Map.of("id", "w1", "label", "mitochondria")),
                                "wordSlots", List.of(Map.of("id", "slot-1", "label", "Powerhouse", "correctWordId", "w1"))),
                        "choices", List.of(choice("w1", "mitochondria", true))),
                        List.of("choices"), List.of()),
                new Case(editorSlide("hotspot", Map.of("kind", "hotspot",
                                "hotspotImage", Map.of("url", "https://cdn/map.png", "width", 1200, "height", 675),
                                "hotspotAreas", areas),
                        "choices", List.of(choice("x1", "Ha Long secret", true)), "hotspotAreas", areas),
                        List.of("choices", "regions", "hotspotAreas"), List.of("Ha Long secret")),
                new Case(editorSlide("drag-and-drop", Map.of("kind", "drag_drop",
                                "dropTargets", List.of(Map.of("id", "drop-target-fruit", "label", "Fruit"),
                                        Map.of("id", "drop-target-vegetable", "label", "Vegetable")),
                                "dragDropItems", List.of(
                                        Map.of("id", "d1", "label", "Apple", "correctTargetId", "drop-target-fruit"),
                                        Map.of("id", "d2", "label", "Carrot", "correctTargetId", "drop-target-vegetable"))),
                        "choices", List.of(),
                        "dragDropItems", List.of(
                                Map.of("id", "d1", "label", "Apple", "emoji", "A", "target", "Fruit",
                                        "targetMedia", Map.of("src", "https://cdn/fruit.png")),
                                Map.of("id", "d2", "label", "Carrot", "emoji", "C", "target", "Vegetable"))),
                        List.of("choices", "targets"), List.of()),
                new Case(editorSlide("likert-scale", Map.of("kind", "likert_scale",
                                "likertRows", List.of(Map.of("id", "l1", "label", "I like math")),
                                "likertScale", List.of(Map.of("id", "scale-1", "label", "Agree", "value", 1))),
                        "choices", List.of(choice("l1", "I like math", true))),
                        List.of("choices"), List.of()),
                new Case(editorSlide("essay", Map.of("kind", "essay",
                                "essayRubric", List.of(Map.of("id", "e1", "label", "Mentions X", "points", 1))),
                        "choices", List.of(choice("e1", "Mentions X", true))),
                        List.of("choices"), List.of())
        );
        assertThat(cases).hasSize(14);

        SoftAssertions.assertSoftly(softly -> {
            for (Case testCase : cases) {
                CompiledQuestion question = compileSlides(testCase.slide()).questions().getFirst();
                String kind = question.kind();
                Set<String> keys = new HashSet<>();
                collectKeys(question.body(), keys);
                Set<String> forbidden = new HashSet<>(ANSWER_KEYS);
                forbidden.addAll(testCase.forbiddenKeys());
                softly.assertThat(keys).as(kind).doesNotContainAnyElementsOf(forbidden);
                for (String value : testCase.forbiddenValues()) {
                    softly.assertThat(question.body().toString()).as(kind).doesNotContain(value);
                }
                // Stripping the body must not starve the answer key.
                softly.assertThat(QuestionKindStrategy.forRuntimeKind(kind).missingAnswerKey(question.answerKey()))
                        .as(kind + " answer key").isNull();
            }
        });
    }

    @Test
    void dragDropKeyUsesTheRuntimeTargetIdsTheLearnerSubmits() {
        List<Map<String, Object>> dropTargets = List.of(Map.of("id", "drop-target-fruit", "label", "Fruit"),
                Map.of("id", "drop-target-vegetable", "label", "Vegetable"));
        List<Map<String, Object>> editorItems = List.of(
                Map.of("id", "d1", "label", "Apple", "emoji", "A", "target", "Fruit"),
                Map.of("id", "d2", "label", "Carrot", "emoji", "C", "target", "Vegetable"));
        Map<String, Object> learnerAnswer = Map.of("dragDropPlacements",
                Map.of("d1", "drop-target-fruit", "d2", "drop-target-vegetable"));
        GradingEngine engine = new GradingEngine();

        // Runtime items carry correctTargetId; only the editor shape carries the group label.
        CompiledQuestion withRuntimeItems = compileSlides(editorSlide("drag-and-drop", Map.of("kind", "drag_drop",
                "dropTargets", dropTargets,
                "dragDropItems", List.of(
                        Map.of("id", "d1", "label", "Apple", "correctTargetId", "drop-target-fruit"),
                        Map.of("id", "d2", "label", "Carrot", "correctTargetId", "drop-target-vegetable"))),
                "dragDropItems", editorItems)).questions().getFirst();
        // Runtime targets only: the editor's group label is mapped to the runtime target id.
        CompiledQuestion withEditorItems = compileSlides(editorSlide("drag-and-drop", Map.of("kind", "drag_drop",
                "dropTargets", dropTargets), "dragDropItems", editorItems)).questions().getFirst();

        for (CompiledQuestion question : List.of(withRuntimeItems, withEditorItems)) {
            assertThat(question.answerKey().get("targets")).isEqualTo(List.of(
                    Map.of("itemId", "d1", "targetId", "drop-target-fruit"),
                    Map.of("itemId", "d2", "targetId", "drop-target-vegetable")));
            assertThat(engine.grade("drag_drop", learnerAnswer, question.answerKey(), BigDecimal.TEN).correct()).isTrue();
        }
    }

    /** The FE writes one textBlank per accepted answer; any of them is right, whichever input the learner used. */
    @Test
    void shortAnswerAcceptsEveryBlanksAnswerAndNeedsOneNonBlankAnswerToPublish() {
        CompiledQuestion question = compileSlides(editorSlide("short-answer", Map.of("kind", "short_answer",
                "textBlanks", List.of(
                        Map.of("id", "a1", "label", "Answer", "correctAnswers", List.of("Ha Noi")),
                        Map.of("id", "a2", "label", "Answer", "correctAnswers", List.of("Hanoi")))))).questions().getFirst();
        GradingEngine engine = new GradingEngine();
        for (String text : List.of("Ha Noi", "  hanoi ", "HANOI")) {
            assertThat(engine.grade("short_answer", Map.of("textResponses", Map.of("a1", text)), question.answerKey(),
                    BigDecimal.TEN).correct()).as(text).isTrue();
        }
        assertThat(engine.grade("short_answer", Map.of("textResponses", Map.of("a1", "Hue")), question.answerKey(),
                BigDecimal.TEN).correct()).isFalse();
        assertThat(QuestionKindStrategy.SHORT_ANSWER.missingAnswerKey(question.answerKey())).isNull();

        Map<String, Object> blankOnly = Map.of("blanks", List.of(Map.of("id", "a1", "answers", List.of(" ", ""))));
        assertThat(QuestionKindStrategy.SHORT_ANSWER.missingAnswerKey(blankOnly)).isEqualTo("accepted answers");
        // An empty accepted answer never makes an empty response correct.
        assertThat(engine.grade("short_answer", Map.of("textResponses", Map.of()), blankOnly, BigDecimal.TEN).correct())
                .isFalse();
    }

    @Test
    void dragWordsWordBankIsShuffledAwayFromSlotOrderAndStillGrades() {
        List<Object> words = new ArrayList<>();
        List<Object> slots = new ArrayList<>();
        Map<String, Object> placements = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            words.add(Map.of("id", "w" + i, "label", "word " + i));
            slots.add(Map.of("id", "slot-" + i, "label", "Slot " + i, "correctWordId", "w" + i));
            placements.put("slot-" + i, "w" + i);
        }
        for (int run = 0; run < 20; run++) {
            CompiledQuestion q = compileSlides(editorSlide("drag-the-words", Map.of("kind", "drag_words",
                    "wordBank", words, "wordSlots", slots))).questions().getFirst();
            assertThat(ids(q.body().get("wordBank"))).isNotEqualTo(List.of("w1", "w2", "w3", "w4", "w5"))
                    .containsExactlyInAnyOrder("w1", "w2", "w3", "w4", "w5");
            assertThat(ids(q.body().get("wordSlots"))).containsExactly("slot-1", "slot-2", "slot-3", "slot-4", "slot-5");
            assertThat(new GradingEngine().grade("drag_words", Map.of("dragWordPlacements", placements), q.answerKey(),
                    BigDecimal.TEN).correct()).isTrue();
        }
    }

    /** Spec §7: the essay marking guide is for teachers; it stays on the authoring slide, never in the player body. */
    @Test
    void essayMarkingGuideStaysOutOfThePlayerBody() {
        Map<String, Object> runtime = Map.of("kind", "essay",
                "essayRubric", List.of(Map.of("id", "e1", "label", "Mentions X", "points", 2)));
        Map<String, Object> slide = editorSlide("essay", runtime, "essayRubric", runtime.get("essayRubric"));

        CompiledQuestion question = compileSlides(slide).questions().getFirst();

        assertThat(question.body()).doesNotContainKey("essayRubric");
        assertThat(question.body().toString()).doesNotContain("Mentions X");
        assertThat(slide).containsKey("essayRubric");
    }

    @Test
    void numericTopLevelValueStaysInTheKeyOnly() {
        CompiledQuestion question = compileSlides(Map.of("slideId", "q-num", "type", "numeric", "value", 8128))
                .questions().getFirst();
        assertThat(question.answerKey()).containsEntry("value", 8128);
        assertThat(question.body()).doesNotContainKey("value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void realEditorSlideKeepsEditorOnlyFieldsOutOfTheLearnerBodyAndStillGrades() {
        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("id", "q1");
        slide.put("kind", "multiple-choice");
        slide.put("title", "Thủ đô?");
        slide.put("status", "draft");
        slide.put("choices", List.of(
                Map.of("id", "a", "label", "Hà Nội", "correct", true),
                Map.of("id", "b", "label", "Huế", "correct", false)));
        slide.put("options", Map.of("score", "By Result", "attempts", 1, "branching", "By Result",
                "layoutPreset", "title-and-content", "answerColumns", 1));
        slide.put("textStyle", Map.of("fontFamily", "Be Vietnam Pro"));
        slide.put("textStyles", Map.of("title", Map.of("fontFamily", "Be Vietnam Pro")));
        slide.put("feedbackRows", List.of(Map.of("id", "feedback-correct", "kind", "correct", "feedback", "Đúng")));
        slide.put("choiceControlType", "radio");
        slide.put("runtimeQuestion", Map.of("id", "q1", "kind", "single_choice", "title", "Thủ đô?", "points", 10,
                "choices", List.of(Map.of("id", "a", "label", "Hà Nội", "correct", true),
                        Map.of("id", "b", "label", "Huế", "correct", false)),
                "feedback", Map.of("correct", "Đúng", "incorrect", "Sai")));

        CompiledQuestion question = compileSlides(slide).questions().getFirst();

        assertThat(question.body()).doesNotContainKeys(
                "status", "options", "textStyle", "textStyles", "feedbackRows", "choiceControlType", "runtimeQuestion");
        assertThat(question.body()).containsEntry("kind", "single_choice").containsKey("choices");
        assertThat(question.body().toString()).doesNotContain("correct=true");
        assertThat(new GradingEngine().grade("single_choice", Map.of("choiceId", "a"), question.answerKey(),
                BigDecimal.TEN).correct()).isTrue();
    }
}
