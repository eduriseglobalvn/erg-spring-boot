package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LmsStatePolicyTest {

    @Test
    void normalizesKnownFiltersAndKeepsPersistedVocabulary() {
        assertThat(LmsStatePolicy.assignmentFilter(" ACTIVE ")).isEqualTo("active");
        assertThat(LmsStatePolicy.assignmentFilter("over-due")).isEqualTo("overdue");
        assertThat(LmsStatePolicy.attemptFilter("NEEDS REVIEW")).isEqualTo("needs_review");
        assertThat(LmsStatePolicy.attemptFilter(null)).isNull();
    }

    @Test
    void rejectsUnknownFiltersInsteadOfSilentlyBroadeningTheQuery() {
        assertThatThrownBy(() -> LmsStatePolicy.assignmentFilter("everything"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unsupported assignment status");
        assertThatThrownBy(() -> LmsStatePolicy.attemptFilter("done-ish"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unsupported attempt status");
    }

    @Test
    void classifiesTerminalReviewAndVisibleStates() {
        assertThat(LmsStatePolicy.attemptIsTerminal("submitted")).isTrue();
        assertThat(LmsStatePolicy.attemptIsTerminal("in_progress")).isFalse();
        assertThat(LmsStatePolicy.attemptNeedsReview("review_required")).isTrue();
        assertThat(LmsStatePolicy.attemptIsEditable("in_progress")).isTrue();
        assertThat(LmsStatePolicy.attemptIsEditable("needs_review")).isFalse();
        assertThat(LmsStatePolicy.completedAttemptStates())
                .containsExactly("submitted", "needs_review", "review_required", "graded", "completed");
        assertThat(LmsStatePolicy.assignmentIsVisible("archived")).isFalse();
        assertThat(LmsStatePolicy.assignmentIsVisible("active")).isTrue();
    }
}
