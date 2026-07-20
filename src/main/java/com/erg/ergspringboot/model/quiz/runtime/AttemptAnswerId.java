package com.erg.ergspringboot.model.quiz.runtime;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link AttemptAnswerRow}: one graded row per (attempt, published question). */
public class AttemptAnswerId implements Serializable {

    private String attemptId;
    private String quizVersionQuestionId;

    public AttemptAnswerId() {
    }

    public AttemptAnswerId(String attemptId, String quizVersionQuestionId) {
        this.attemptId = attemptId;
        this.quizVersionQuestionId = quizVersionQuestionId;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getQuizVersionQuestionId() {
        return quizVersionQuestionId;
    }

    public void setQuizVersionQuestionId(String quizVersionQuestionId) {
        this.quizVersionQuestionId = quizVersionQuestionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttemptAnswerId that)) {
            return false;
        }
        return Objects.equals(attemptId, that.attemptId)
                && Objects.equals(quizVersionQuestionId, that.quizVersionQuestionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId, quizVersionQuestionId);
    }
}
