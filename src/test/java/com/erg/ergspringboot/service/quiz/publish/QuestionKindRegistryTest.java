package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionKindRegistryTest {

    @Test
    void everyPublishedKindHasOneImmutableDescriptorAcrossPhases() {
        assertThat(QuestionKindRegistry.all()).hasSize(14);
        assertThat(QuestionKindRegistry.all()).allSatisfy(descriptor -> {
            assertThat(descriptor.runtimeKind()).isNotBlank();
            assertThat(descriptor.authoringAliases()).isNotEmpty();
            assertThat(descriptor.gradingPolicy()).isIn("auto", "manual", "survey");
            assertThat(descriptor.answerKeyRule()).isNotNull();
            assertThat(descriptor.gradingRule()).isNotNull();
        });
        assertThat(new HashSet<>(QuestionKindRegistry.all().stream()
                .map(QuestionKindDescriptor::runtimeKind).toList())).hasSize(14);
        assertThat(QuestionKindRegistry.all().stream().filter(d -> !d.autoGradable()).toList())
                .extracting(QuestionKindDescriptor::runtimeKind)
                .containsExactlyInAnyOrder("essay", "likert_scale");
    }

    @Test
    void aliasesResolveToCanonicalRuntimeKindsAndUnknownKindsFailClosed() {
        assertThat(QuestionKindMapping.forAuthoringType(" MULTIPLE-CHOICE ").runtimeKind())
                .isEqualTo("single_choice");
        assertThat(QuestionKindMapping.isQuestionType("instruction-slide")).isFalse();
        assertThat(QuestionKindRegistry.requireRuntimeKind("drag_drop").gradingRule())
                .isEqualTo(QuestionKindDescriptor.GradingRule.PAIR_RATIO);
        assertThatThrownBy(() -> QuestionKindRegistry.requireRuntimeKind("future_kind"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported question kind");
    }

    @Test
    void phaseSwitchesDoNotKeepIndependentRawKindLists() throws Exception {
        String grading = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/quiz/runtime/GradingEngine.java"));
        String validation = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/quiz/publish/QuizValidationService.java"));
        String compile = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/quiz/publish/QuizCompileService.java"));
        assertThat(grading).doesNotContain("case \"single_choice\"");
        assertThat(validation).doesNotContain("switch (question.kind())");
        assertThat(compile).doesNotContain("switch (runtimeKind)");
        String compilers = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/quiz/publish/QuestionKindCompiler.java"));
        assertThat(compilers).doesNotContain("case \"single_choice\"");
    }

    @Test
    void everyRegisteredKindResolvesOneCompileStrategy() {
        assertThat(QuestionKindRegistry.all()).allSatisfy(descriptor ->
                assertThat(QuestionKindCompiler.forRuntimeKind(descriptor.runtimeKind()).name())
                        .isEqualTo(descriptor.answerKeyRule().name()));
        assertThatThrownBy(() -> QuestionKindCompiler.forRuntimeKind("future_kind"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported question kind");
    }

    @Test
    void stripRunsEveryKindsStrategySoForeignCorrectnessMarkersNeverLeak() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("options", List.of(Map.of("id", "a", "correct", true)));
        body.put("hotspotAreas", List.of(Map.of("id", "h", "correct", true)));
        body.put("wordSlots", List.of(Map.of("id", "w", "correctWordId", "x")));
        body.put("numericAnswer", Map.of("correctValue", 4, "unit", "cm"));
        body.put("correctAnswer", "a");

        QuestionKindCompiler.stripAllAnswers(body);

        assertThat(body).containsOnlyKeys("options", "hotspotAreas", "wordSlots", "numericAnswer");
        assertThat(body.get("options")).isEqualTo(List.of(Map.of("id", "a")));
        assertThat(body.get("hotspotAreas")).isEqualTo(List.of(Map.of("id", "h")));
        assertThat(body.get("wordSlots")).isEqualTo(List.of(Map.of("id", "w")));
        assertThat(body.get("numericAnswer")).isEqualTo(Map.of("unit", "cm"));
    }
}
