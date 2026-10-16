package com.erg.ergspringboot.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.BadRequestException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LifecycleTransitionPolicyTest {

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void acceptsOnlyTheCanonicalTransitionMatrix(
            LifecycleState from, LifecycleState to) {
        LifecycleTransition result = LifecycleTransitionPolicy.validate(from, to, "change requested");

        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
    }

    @ParameterizedTest
    @MethodSource("illegalTransitions")
    void rejectsIllegalTransitions(LifecycleState from, LifecycleState to) {
        assertThatThrownBy(() -> LifecycleTransitionPolicy.validate(from, to, "change requested"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("illegal lifecycle transition");
    }

    @Test
    void archiveAndReviewRejectionRequireAReason() {
        assertThatThrownBy(() -> LifecycleTransitionPolicy.validate(
                LifecycleState.DRAFT, LifecycleState.ARCHIVED, " "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");
        assertThatThrownBy(() -> LifecycleTransitionPolicy.validate(
                LifecycleState.IN_REVIEW, LifecycleState.DRAFT, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void validatedQuizPublishIsASeparateInternalBridge() {
        LifecycleTransition result = LifecycleTransitionPolicy.validateValidatedQuizPublish(
                LifecycleState.DRAFT, "compiler validation passed");

        assertThat(result.to()).isEqualTo(LifecycleState.PUBLISHED);
        assertThatThrownBy(() -> LifecycleTransitionPolicy.validateValidatedQuizPublish(
                LifecycleState.ARCHIVED, "compiler validation passed"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("restored before publishing");
    }

    @Test
    void quizRollbackFromPublishedOrArchivedIsMarkedAsForkOperation() {
        assertThat(LifecycleTransitionPolicy.validate(
                LifecycleState.PUBLISHED, LifecycleState.DRAFT, "restore", true).rollback()).isTrue();
        assertThat(LifecycleTransitionPolicy.validate(
                LifecycleState.ARCHIVED, LifecycleState.DRAFT, "restore", true).rollback()).isTrue();
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(LifecycleState.DRAFT, LifecycleState.IN_REVIEW),
                Arguments.of(LifecycleState.DRAFT, LifecycleState.ARCHIVED),
                Arguments.of(LifecycleState.IN_REVIEW, LifecycleState.APPROVED),
                Arguments.of(LifecycleState.IN_REVIEW, LifecycleState.DRAFT),
                Arguments.of(LifecycleState.IN_REVIEW, LifecycleState.ARCHIVED),
                Arguments.of(LifecycleState.APPROVED, LifecycleState.PUBLISHED),
                Arguments.of(LifecycleState.APPROVED, LifecycleState.DRAFT),
                Arguments.of(LifecycleState.APPROVED, LifecycleState.ARCHIVED),
                Arguments.of(LifecycleState.PUBLISHED, LifecycleState.ARCHIVED),
                Arguments.of(LifecycleState.ARCHIVED, LifecycleState.DRAFT));
    }

    private static Stream<Arguments> illegalTransitions() {
        return Stream.of(
                Arguments.of(LifecycleState.DRAFT, LifecycleState.APPROVED),
                Arguments.of(LifecycleState.DRAFT, LifecycleState.PUBLISHED),
                Arguments.of(LifecycleState.IN_REVIEW, LifecycleState.PUBLISHED),
                Arguments.of(LifecycleState.APPROVED, LifecycleState.IN_REVIEW),
                Arguments.of(LifecycleState.ARCHIVED, LifecycleState.PUBLISHED),
                Arguments.of(LifecycleState.PUBLISHED, LifecycleState.IN_REVIEW));
    }
}
