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

    @Test
    void needsReviewCountsAsHandedInEverywhere() throws Exception {
        assertThat(LmsStatePolicy.attemptIsCompleted("needs_review")).isTrue();
        assertThat(LmsStatePolicy.attemptIsCompleted("in_progress")).isFalse();
        // SQ-03: JPQL read models take the state list as a parameter instead of re-declaring it.
        for (String repository : java.util.List.of("AttemptRepository", "AssignmentRepository", "StudentRepository")) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/erg/ergspringboot/repository/lms/" + repository + ".java"));
            assertThat(source).as(repository).doesNotContain("('submitted', 'graded', 'completed')")
                    .doesNotContain("('submitted', 'needs_review', 'review_required')");
        }
        String dashboard = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardReadService.java"));
        assertThat(dashboard).contains("LmsStatePolicy.attemptIsCompleted");
    }

    @Test
    void storedAssignmentStatusIsValidated() {
        assertThat(LmsStatePolicy.assignmentState(" Archived ")).isEqualTo("archived");
        assertThatThrownBy(() -> LmsStatePolicy.assignmentState("foo")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> LmsStatePolicy.assignmentState("overdue")).isInstanceOf(RuntimeException.class);
    }
}
