package com.erg.ergspringboot.common.response.lms;

import java.time.OffsetDateTime;
import java.util.List;

/** The class score board: the roster and every handed-in attempt; the client folds them into its two tables. */
public final class LmsClassScoreBoardResponses {
    private LmsClassScoreBoardResponses() {
    }

    public record ClassScoreBoardResponse(String classId, String className, List<ScoreBoardStudent> students,
            List<ScoreBoardAttempt> attempts, boolean truncated) {
    }

    public record ScoreBoardStudent(String id, String studentCode, String fullName) {
    }

    public record ScoreBoardAttempt(String id, String studentId, String assignmentId, String assignmentTitle, String quizId,
            String quizTitle, String topicId, String topicName, Double score, Double maxScore, Double percent, String status,
            OffsetDateTime startedAt, OffsetDateTime submittedAt, Integer durationSeconds) {
    }
}
