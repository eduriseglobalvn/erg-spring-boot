package com.erg.ergspringboot.common.request.quiz;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * LMS distribution write payloads (spec §4.8). An assignment carries one or more quizzes (each pins the
 * active published version) and a recipient set resolved from a mode (class / group / students) into a
 * flat per-student recipient list. Create is idempotent on {@code X-Idempotency-Key}.
 */
public final class AssignmentRequests {

    private AssignmentRequests() {
    }

    public record CreateAssignmentRequest(
            String title,
            List<String> quizIds,
            String recipientMode,
            String classId,
            String groupId,
            List<String> studentIds,
            OffsetDateTime startAt,
            OffsetDateTime dueAt,
            Integer attemptLimit,
            Integer maxDurationMinutes,
            String teacherNote,
            Boolean offlineAllowed
    ) {
        public CreateAssignmentRequest(
                String title,
                List<String> quizIds,
                String recipientMode,
                String classId,
                String groupId,
                List<String> studentIds,
                OffsetDateTime startAt,
                OffsetDateTime dueAt,
                Integer attemptLimit,
                Integer maxDurationMinutes,
                String teacherNote
        ) {
            this(title, quizIds, recipientMode, classId, groupId, studentIds, startAt, dueAt,
                    attemptLimit, maxDurationMinutes, teacherNote, null);
        }
    }

    public record UpdateAssignmentRequest(
            String title,
            OffsetDateTime startAt,
            OffsetDateTime dueAt,
            Integer attemptLimit,
            Integer maxDurationMinutes,
            String teacherNote,
            String status,
            Boolean offlineAllowed
    ) {
        public UpdateAssignmentRequest(
                String title,
                OffsetDateTime startAt,
                OffsetDateTime dueAt,
                Integer attemptLimit,
                Integer maxDurationMinutes,
                String teacherNote,
                String status
        ) {
            this(title, startAt, dueAt, attemptLimit, maxDurationMinutes, teacherNote, status, null);
        }
    }

    public record UpdateRecipientsRequest(
            String recipientMode,
            String classId,
            String groupId,
            List<String> studentIds
    ) {
    }

    public record StudentGroupRequest(
            String name,
            String classId,
            String note,
            String color,
            List<String> studentIds
    ) {
    }
}
