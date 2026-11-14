package com.erg.ergspringboot.elearning.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAssignmentCardResponse;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentProjection;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class StudentDashboardCardsAssignmentTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-12T10:00:00+07:00");

    private static StudentDashboardAssignmentProjection projection(OffsetDateTime dueAt, String latestStatus, Double handedInPercent,
                                                                   OffsetDateTime handedInAt, Boolean late, String kind) {
        return new StudentDashboardAssignmentProjection("a1", "c1", "q1", "math", "Toán", "Bài 1", dueAt, "active", NOW.minusDays(3),
                "t1", latestStatus, 50D, handedInAt, NOW.minusDays(1), handedInPercent, handedInAt,
                15, "Cô Lan", kind, 20, 2L, 72.5, 100D, 22, late, 12, 20, 18L);
    }

    @Test
    void notHandedInYetShowsProgressAndXpStillToEarn() {
        StudentDashboardAssignmentCardResponse card = StudentDashboardCards.toAssignmentCard(
                projection(NOW.plusDays(1), "in_progress", null, null, null, "train"), NOW);
        assertThat(card.subjectName()).isEqualTo("Toán");
        assertThat(card.teacherName()).isEqualTo("Cô Lan");
        assertThat(card.questionCount()).isEqualTo(20);
        assertThat(card.estimatedMinutes()).isEqualTo(15);
        assertThat(card.kind()).isEqualTo("PRACTICE");
        assertThat(card.answeredCount()).isEqualTo(12);
        assertThat(card.xpReward()).isEqualTo(40 + 10); // min(40, 10 + 30) + on-time bonus
        assertThat(card.onTime()).isNull();
        assertThat(card.closed()).isFalse();
        assertThat(card.attemptCount()).isEqualTo(2);
        assertThat(card.correctCount()).isNull();
        assertThat(card.lateSubmitUntil()).isNull();
    }

    @Test
    void handedInKeepsRawScoreOnTimeMarginAndNoMoreXp() {
        OffsetDateTime due = NOW.plusDays(4);
        StudentDashboardAssignmentCardResponse card = StudentDashboardCards.toAssignmentCard(
                projection(due, "submitted", 72.5, NOW.minusHours(1), false, "test"), NOW);
        assertThat(card.kind()).isEqualTo("TEST");
        assertThat(card.answeredCount()).isNull();
        assertThat(card.score()).isEqualTo(72.5);
        assertThat(card.maxScore()).isEqualTo(100D);
        assertThat(card.totalQuestions()).isEqualTo(22);
        assertThat(card.correctCount()).isEqualTo(18);
        assertThat(card.onTime()).isTrue();
        assertThat(card.minutesBeforeDue()).isEqualTo(4 * 24 * 60 + 60);
        assertThat(card.xpReward()).isZero();
    }

    @Test
    void pastDueIsClosedAndLateHandInIsNotOnTime() {
        StudentDashboardAssignmentCardResponse open = StudentDashboardCards.toAssignmentCard(
                projection(NOW.minusDays(1), null, null, null, null, "train"), NOW);
        assertThat(open.closed()).isTrue();
        assertThat(open.xpReward()).isEqualTo(40); // no on-time bonus any more
        StudentDashboardAssignmentCardResponse late = StudentDashboardCards.toAssignmentCard(
                projection(NOW.minusDays(1), "submitted", 50D, NOW.minusHours(1), true, "train"), NOW);
        assertThat(late.onTime()).isFalse();
        assertThat(late.minutesBeforeDue()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(nullValues = "null", value = {
            "submitted,true", "needs_review,true", "review_required,true", "graded,true", "completed,true",
            "GRADED,true", "in_progress,false", "started,false", "done,false", "null,false"})
    void handedInFlagFollowsTheServerHandedInStates(String latestStatus, boolean handedIn) {
        StudentDashboardAssignmentCardResponse card = StudentDashboardCards.toAssignmentCard(
                projection(NOW.plusDays(1), latestStatus, null, null, null, "train"), NOW);
        assertThat(card.handedIn()).isEqualTo(handedIn);
    }
}
