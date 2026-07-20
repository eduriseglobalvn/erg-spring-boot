package com.erg.ergspringboot.service.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.service.quiz.runtime.GradingEngine.GradeResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden per-kind grading tests (spec §6). Proves the server recompute matches the expected result for
 * every runtime kind so client-first and server-authoritative modes agree.
 */
class GradingEngineTest {

    private final GradingEngine engine = new GradingEngine();
    private static final BigDecimal TEN = BigDecimal.valueOf(10);

    @Test
    void singleChoiceExactMatch() {
        GradeResult right = engine.grade("single_choice", Map.of("choiceId", "b"),
                Map.of("correctChoiceId", "b"), TEN);
        assertThat(right.correct()).isTrue();
        assertThat(right.awardedPoints()).isEqualByComparingTo(TEN);

        GradeResult wrong = engine.grade("single_choice", Map.of("choiceId", "a"),
                Map.of("correctChoiceId", "b"), TEN);
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.awardedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void trueFalseUsesSameChoiceRule() {
        GradeResult result = engine.grade("true_false", Map.of("choiceId", "true"),
                Map.of("correctChoiceId", "true"), TEN);
        assertThat(result.correct()).isTrue();
    }

    @Test
    void multipleResponseAllOrNothingByDefault() {
        Map<String, Object> key = Map.of("correctChoiceIds", List.of("a", "b"));
        GradeResult partial = engine.grade("multiple_response", Map.of("choiceIds", List.of("a")), key, TEN);
        assertThat(partial.correct()).isFalse();
        assertThat(partial.awardedPoints()).isEqualByComparingTo(BigDecimal.ZERO);

        GradeResult full = engine.grade("multiple_response", Map.of("choiceIds", List.of("a", "b")), key, TEN);
        assertThat(full.correct()).isTrue();
        assertThat(full.awardedPoints()).isEqualByComparingTo(TEN);
    }

    @Test
    void multipleResponsePartialCreditWhenAccepted() {
        Map<String, Object> key = Map.of("correctChoiceIds", List.of("a", "b", "c", "d"), "acceptPartial", true);
        // 3 right, 0 wrong of 4 -> 0.75 * 10 = 7.5
        GradeResult result = engine.grade("multiple_response", Map.of("choiceIds", List.of("a", "b", "c")), key, TEN);
        assertThat(result.partiallyRight()).isTrue();
        assertThat(result.correct()).isFalse();
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.valueOf(7.5));
    }

    @Test
    void multipleResponsePenalizesWrongChoices() {
        Map<String, Object> key = Map.of("correctChoiceIds", List.of("a", "b"), "acceptPartial", true);
        // 1 right, 1 wrong of 2 correct -> (1-1)/2 = 0
        GradeResult result = engine.grade("multiple_response", Map.of("choiceIds", List.of("a", "x")), key, TEN);
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shortAnswerNormalizesCaseAndSpace() {
        Map<String, Object> key = Map.of("correctAnswers", List.of("Paris", "paris "));
        GradeResult result = engine.grade("short_answer", Map.of("text", "  PARIS "), key, TEN);
        assertThat(result.correct()).isTrue();
    }

    @Test
    void numericWithinTolerance() {
        Map<String, Object> key = Map.of("value", 3.14, "tolerance", 0.01);
        assertThat(engine.grade("numeric", Map.of("value", 3.15), key, TEN).correct()).isTrue();
        assertThat(engine.grade("numeric", Map.of("value", 3.2), key, TEN).correct()).isFalse();
    }

    @Test
    void sequencePerPositionRatio() {
        Map<String, Object> key = Map.of("order", List.of("1", "2", "3", "4"));
        // 2 of 4 positions right -> 5.0
        GradeResult result = engine.grade("sequence", Map.of("order", List.of("1", "2", "4", "3")), key, TEN);
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
        assertThat(result.partiallyRight()).isTrue();
    }

    @Test
    void matchingPairRatio() {
        Map<String, Object> key = Map.of("pairs", List.of(
                Map.of("promptId", "p1", "responseId", "r1"),
                Map.of("promptId", "p2", "responseId", "r2")
        ));
        GradeResult result = engine.grade("matching", Map.of("pairs", Map.of("p1", "r1", "p2", "r9")), key, TEN);
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
    }

    @Test
    void fillBlankRatio() {
        Map<String, Object> key = Map.of("blanks", List.of(
                Map.of("id", "b1", "answers", List.of("cat")),
                Map.of("id", "b2", "answers", List.of("dog"))
        ));
        GradeResult result = engine.grade("fill_blank", Map.of("blanks", Map.of("b1", "Cat", "b2", "fish")), key, TEN);
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
    }

    @Test
    void hotspotPointInRegion() {
        Map<String, Object> key = Map.of("regions", List.of(
                Map.of("x", 10.0, "y", 10.0, "width", 20.0, "height", 20.0)
        ));
        assertThat(engine.grade("hotspot", Map.of("x", 15.0, "y", 15.0), key, TEN).correct()).isTrue();
        assertThat(engine.grade("hotspot", Map.of("x", 100.0, "y", 5.0), key, TEN).correct()).isFalse();
    }

    @Test
    void acceptsFrontendAnswerPayloadAliases() {
        assertThat(engine.grade("numeric", Map.of("numericValue", "3.15"),
                Map.of("value", 3.14, "tolerance", 0.01), TEN).correct()).isTrue();

        assertThat(engine.grade("sequence", Map.of("sequenceOrder", List.of("a", "b")),
                Map.of("order", List.of("a", "b")), TEN).correct()).isTrue();

        assertThat(engine.grade("fill_blank", Map.of("textResponses", Map.of("b1", "Cat")),
                Map.of("blanks", List.of(Map.of("id", "b1", "answers", List.of("cat")))), TEN).correct()).isTrue();

        assertThat(engine.grade("hotspot", Map.of("hotspotPoint", Map.of("x", 15, "y", 15)),
                Map.of("regions", List.of(Map.of("x", 10, "y", 10, "width", 20, "height", 20))), TEN).correct()).isTrue();
    }

    @Test
    void shortAnswerFallsBackToSingleTextBlankKey() {
        GradeResult result = engine.grade("short_answer", Map.of("textResponses", Map.of("b1", "Cat")),
                Map.of("blanks", List.of(Map.of("id", "b1", "answers", List.of("cat")))), TEN);
        assertThat(result.correct()).isTrue();
    }

    @Test
    void likertIsSurveyWithNoScore() {
        GradeResult result = engine.grade("likert_scale", Map.of("value", 4), Map.of(), TEN);
        assertThat(result.correct()).isNull();
        assertThat(result.awardedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.needsManualGrade()).isFalse();
    }

    @Test
    void essayNeedsManualGrade() {
        GradeResult result = engine.grade("essay", Map.of("text", "an answer"), Map.of(), TEN);
        assertThat(result.correct()).isNull();
        assertThat(result.needsManualGrade()).isTrue();
    }
}
