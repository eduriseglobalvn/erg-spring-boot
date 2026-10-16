package com.erg.ergspringboot.common.dto.lms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class LmsRuntimeDtos {

    private LmsRuntimeDtos() {
    }

    public record ContentScope(String type, String schoolId) {
    }

    public record EducationUnitItem(String id, String name) { }
    public record EducationUnitListResponse(List<EducationUnitItem> items) { }
    public record AccessScopeResponse(String tenantId, String currentEducationUnitId, String currentClassId,
            List<String> managedEducationUnitIds, List<String> managedClassIds) { }
    public record ClassItem(String id, String name, String schoolId, String grade, String academicYear) { }
    public record ClassesBySchoolResponse(String schoolId, List<ClassItem> items, AccessScopeResponse scope) { }

    public record QuestionChoice(String id, String label, boolean correct) {
    }

    public record QuizResponse(
            String id,
            ContentScope scope,
            String title,
            String kind,
            String subjectId,
            String levelId,
            List<String> topicIds,
            List<String> questionIds,
            String status,
            int version,
            String packageHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record QuizDetailResponse(
            QuizResponse quiz,
            List<Object> slides,
            Map<String, Object> settings,
            Map<String, Object> result,
            Map<String, Object> theme
    ) {
    }

    public record QuizPackageResponse(
            int version,
            String packageHash,
            String contentHash,
            String signature,
            String gradingMode,
            QuizDetailResponse quiz,
            Map<String, Object> answerKey
    ) {
    }

    public record CreateAssignmentRequest(
            @NotBlank String classId,
            @NotEmpty List<String> quizIds,
            @NotBlank String recipientMode,
            List<String> studentIds,
            OffsetDateTime dueAt,
            String teacherNote
    ) {
    }

    public record AssignmentResponse(
            String id,
            String classId,
            String quizId,
            String subjectId,
            List<String> studentIds,
            OffsetDateTime dueAt,
            String teacherNote,
            String status,
            String recipientMode,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            boolean offlineAllowed
    ) {
    }

    public record AssignmentBatchResponse(List<String> assignmentIds, int recipientCount) {
    }

    public record AssignmentProgressResponse(int submitted, int inProgress, int notStarted, int needsReview) {
    }

    public record StudentAssignmentListResponse(
            List<AssignmentResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            boolean hasNext
    ) {
        public StudentAssignmentListResponse(List<AssignmentResponse> items) {
            this(items, 0, items == null ? 0 : items.size(), items == null ? 0 : items.size(), 1, false);
        }
    }

    public record AttemptAnswer(Object answer, Map<String, Object> clientResult, OffsetDateTime answeredAt) {
    }

    /**
     * Immutable server policy captured when an attempt starts.  A client may cache this
     * object for resume, but it must never recalculate a deadline or package lineage from
     * mutable assignment/quiz data.
     */
    public record AttemptPolicySnapshotResponse(
            String assignmentId,
            String quizId,
            String quizVersionId,
            String packageId,
            String packageHash,
            String teacherId,
            Integer questionCount,
            Integer durationMinutes,
            OffsetDateTime deadlineAt,
            Integer attemptLimit,
            String gradingMode,
            String quizVersion,
            boolean offlineAllowed,
            OffsetDateTime startedAt,
            OffsetDateTime serverTimeAtStart
    ) {
        public static AttemptPolicySnapshotResponse from(Map<String, Object> source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return new AttemptPolicySnapshotResponse(
                    string(source, "assignmentId"),
                    string(source, "quizId"),
                    string(source, "quizVersionId"),
                    string(source, "packageId"),
                    string(source, "packageHash"),
                    string(source, "teacherId"),
                    integer(source, "questionCount"),
                    integer(source, "durationMinutes"),
                    offsetDateTime(source, "deadlineAt"),
                    integer(source, "attemptLimit"),
                    string(source, "gradingMode"),
                    string(source, "quizVersion"),
                    Boolean.TRUE.equals(source.get("offlineAllowed")),
                    offsetDateTime(source, "startedAt"),
                    offsetDateTime(source, "serverTimeAtStart")
            );
        }

        private static String string(Map<String, Object> source, String key) {
            Object value = source.get(key);
            return value == null ? null : value.toString();
        }

        private static Integer integer(Map<String, Object> source, String key) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value == null || value.toString().isBlank()) {
                return null;
            }
            try {
                return Integer.valueOf(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static OffsetDateTime offsetDateTime(Map<String, Object> source, String key) {
            Object value = source.get(key);
            if (value instanceof OffsetDateTime dateTime) {
                return dateTime;
            }
            if (value == null || value.toString().isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(value.toString());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public record AttemptResponse(
            String id,
            String assignmentId,
            String quizId,
            String studentId,
            String packageId,
            String packageHash,
            String status,
            Map<String, AttemptAnswer> answers,
            double score,
            double maxScore,
            double percent,
            boolean passed,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt,
            AttemptPolicySnapshotResponse policySnapshot
    ) {
        /** Source-compatible constructor for list/read-model callers that have no snapshot column. */
        public AttemptResponse(
                String id,
                String assignmentId,
                String quizId,
                String studentId,
                String packageId,
                String packageHash,
                String status,
                Map<String, AttemptAnswer> answers,
                double score,
                double maxScore,
                double percent,
                boolean passed,
                OffsetDateTime startedAt,
                OffsetDateTime submittedAt,
                OffsetDateTime updatedAt
        ) {
            this(id, assignmentId, quizId, studentId, packageId, packageHash, status, answers,
                    score, maxScore, percent, passed, startedAt, submittedAt, updatedAt, null);
        }
    }

    public record StartAttemptRequest(
            @NotBlank String assignmentId,
            @NotBlank String quizId,
            @NotBlank String packageId,
            @NotBlank String packageHash
    ) {
    }

    public record SaveAnswerRequest(
            Object answer,
            Map<String, Object> clientResult,
            OffsetDateTime answeredAt,
            Long expectedVersion,
            String operationId,
            String idempotencyKey
    ) {
        public SaveAnswerRequest(Object answer, Map<String, Object> clientResult, OffsetDateTime answeredAt) {
            this(answer, clientResult, answeredAt, null, null, null);
        }
        public SaveAnswerRequest withIdempotencyKey(String key) {
            return new SaveAnswerRequest(answer, clientResult, answeredAt, expectedVersion, operationId, key);
        }
    }

    public record AttemptDraftRequest(
            Map<String, Object> answers,
            String packageHash,
            String quizVersion,
            List<Map<String, Object>> events,
            Map<String, Object> client,
            Long expectedVersion,
            String operationId,
            String idempotencyKey
    ) {
        public AttemptDraftRequest(Map<String, Object> answers, String packageHash, String quizVersion,
                                   List<Map<String, Object>> events, Map<String, Object> client) {
            this(answers, packageHash, quizVersion, events, client, null, null, null);
        }
        public AttemptDraftRequest withIdempotencyKey(String key) {
            return new AttemptDraftRequest(answers, packageHash, quizVersion, events, client,
                    expectedVersion, operationId, key);
        }
    }

    public record AnswerResultResponse(String attemptId, String questionId, boolean saved) {
    }

    public record SubmitAttemptRequest(
            Map<String, Object> answers,
            OffsetDateTime submittedAt,
            String packageHash,
            String quizVersion,
            Map<String, Object> clientResult,
            List<Map<String, Object>> clientEvents,
            String idempotencyKey
    ) {
        /** Source-compatible constructor; submittedAt is retained for wire compatibility only. */
        public SubmitAttemptRequest(
                Map<String, Object> answers,
                OffsetDateTime submittedAt,
                String packageHash,
                String quizVersion,
                Map<String, Object> clientResult,
                List<Map<String, Object>> clientEvents
        ) {
            this(answers, submittedAt, packageHash, quizVersion, clientResult, clientEvents, null);
        }

        public SubmitAttemptRequest withIdempotencyKey(String key) {
            return new SubmitAttemptRequest(answers, submittedAt, packageHash, quizVersion, clientResult, clientEvents, key);
        }
    }

    public record AttemptSubmitResponse(double score, double maxScore, double percent, boolean passed) {
    }

    public record AttemptSyncRequest(
            @NotBlank String packageHash,
            String quizVersion,
            Map<String, Object> attempt,
            List<Map<String, Object>> events,
            Map<String, Object> client,
            Long expectedVersion,
            String operationId,
            String idempotencyKey
    ) {
        public AttemptSyncRequest(String packageHash, String quizVersion, Map<String, Object> attempt,
                                  List<Map<String, Object>> events, Map<String, Object> client) {
            this(packageHash, quizVersion, attempt, events, client, null, null, null);
        }
        public AttemptSyncRequest withIdempotencyKey(String key) {
            return new AttemptSyncRequest(packageHash, quizVersion, attempt, events, client,
                    expectedVersion, operationId, key);
        }
    }

    public record AttemptSyncResponse(String status, AttemptResponse serverAttempt, List<String> conflicts) {
    }

    public record StudentScoreItem(
            AssignmentResponse assignment,
            double bestScore,
            List<AttemptResponse> recentAttemptsTop3
    ) {
    }

    public record StudentScoreListResponse(
            List<StudentScoreItem> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            boolean hasNext
    ) {
        public StudentScoreListResponse(List<StudentScoreItem> items) {
            this(items, 0, items == null ? 0 : items.size(), items == null ? 0 : items.size(), 1, false);
        }
    }
}
