package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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
    }
}
