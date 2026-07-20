package com.erg.ergspringboot.repository.quiz.runtime;

import com.erg.ergspringboot.model.quiz.runtime.AttemptAnswerId;
import com.erg.ergspringboot.model.quiz.runtime.AttemptAnswerRow;
import com.erg.ergspringboot.repository.quiz.runtime.projection.QuestionAnalyticsProjection;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptAnswerRowRepository extends JpaRepository<AttemptAnswerRow, AttemptAnswerId> {

    List<AttemptAnswerRow> findByAttemptId(String attemptId);

    java.util.Optional<AttemptAnswerRow> findByAttemptIdAndQuizVersionQuestionId(String attemptId, String quizVersionQuestionId);

    List<AttemptAnswerRow> findByAttemptIdAndQuizVersionQuestionIdIn(
            String attemptId, Collection<String> quizVersionQuestionIds);

    /**
     * Per-question analytics for a published version's normalized answers (spec §4.9). Aggregates the
     * {@code attempt_answers} rows for submitted attempts of the given version, so difficulty (correct
     * ratio) and average awarded points come straight from the normalized grading source — no JSONB scan.
     */
    @Query(
            value = """
                    select
                        answer.quiz_version_question_id as questionId,
                        answer.question_kind as questionKind,
                        count(answer.attempt_id) as answeredCount,
                        coalesce(sum(case when answer.correct = true then 1 else 0 end), 0) as correctCount,
                        coalesce(sum(case when answer.partially_right = true then 1 else 0 end), 0) as partialCount,
                        coalesce(sum(case when answer.needs_manual_grade = true then 1 else 0 end), 0) as needsManualCount,
                        coalesce(avg(answer.awarded_points), 0) as avgAwardedPoints,
                        coalesce(max(answer.max_points), 0) as maxPoints
                    from attempt_answers answer
                    join lms_attempts attempt on attempt.id = answer.attempt_id
                    where answer.tenant_id = :tenantId
                      and attempt.tenant_id = :tenantId
                      and attempt.quiz_version_id = :quizVersionId
                      and lower(attempt.status) in ('submitted', 'graded', 'completed', 'needs_review')
                    group by answer.quiz_version_question_id, answer.question_kind
                    """,
            nativeQuery = true
    )
    List<QuestionAnalyticsProjection> findQuestionAnalytics(
            @Param("tenantId") String tenantId,
            @Param("quizVersionId") String quizVersionId
    );
}
