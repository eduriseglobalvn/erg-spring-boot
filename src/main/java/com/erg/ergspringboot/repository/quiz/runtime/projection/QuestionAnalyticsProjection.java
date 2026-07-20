package com.erg.ergspringboot.repository.quiz.runtime.projection;

import java.math.BigDecimal;

/**
 * Per-question difficulty analytics aggregated over {@code attempt_answers} (spec §1.9, §4.9). One row
 * per published question: how many attempts answered it, how many were fully correct / partial / need
 * manual grading, and the average awarded points — the source for the "hardest questions" item analysis.
 *
 * <p>Native-query projection: getter names map to the SQL column aliases in
 * {@code AttemptAnswerRowRepository.findQuestionAnalytics}.
 */
public interface QuestionAnalyticsProjection {

    String getQuestionId();

    String getQuestionKind();

    long getAnsweredCount();

    long getCorrectCount();

    long getPartialCount();

    long getNeedsManualCount();

    BigDecimal getAvgAwardedPoints();

    BigDecimal getMaxPoints();
}
