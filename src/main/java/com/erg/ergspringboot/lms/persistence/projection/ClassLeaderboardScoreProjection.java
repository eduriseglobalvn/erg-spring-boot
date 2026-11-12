package com.erg.ergspringboot.lms.persistence.projection;

/** One student's handed-in work in a class: assignments counted once (latest handed-in attempt) and their average percent. */
public record ClassLeaderboardScoreProjection(String studentId, Long completedCount, Double averagePercent) {
}
