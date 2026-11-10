package com.erg.ergspringboot.lms.persistence;

/**
 * JPQL filters that keep only a student's latest handed-in attempt per assignment (newest update, then id), so the
 * dashboard card's score and the dashboard average count the same attempt. One constant per attempt alias.
 */
final class LatestHandedInAttempt {
    static final String OF_ATTEMPT = """
            and not exists (select newer.id from Attempt newer
                where newer.tenantId = attempt.tenantId and newer.assignmentId = attempt.assignmentId
                  and newer.studentId = attempt.studentId and lower(newer.status) in :completedStates
                  and (newer.updatedAt > attempt.updatedAt or (newer.updatedAt = attempt.updatedAt and newer.id > attempt.id)))
            """;
    static final String OF_HANDED_IN = """
            and not exists (select newer.id from Attempt newer
                where newer.tenantId = handedIn.tenantId and newer.assignmentId = handedIn.assignmentId
                  and newer.studentId = handedIn.studentId and lower(newer.status) in :completedStates
                  and (newer.updatedAt > handedIn.updatedAt or (newer.updatedAt = handedIn.updatedAt and newer.id > handedIn.id)))
            """;

    private LatestHandedInAttempt() {
    }
}
