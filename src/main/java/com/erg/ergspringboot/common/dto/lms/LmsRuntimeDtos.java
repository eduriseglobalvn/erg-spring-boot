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
            OffsetDateTime updatedAt
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
            OffsetDateTime updatedAt
    ) {
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
            OffsetDateTime answeredAt
    ) {
    }

    public record AttemptDraftRequest(
            Map<String, Object> answers,
            String packageHash,
            String quizVersion,
            List<Map<String, Object>> events,
            Map<String, Object> client
    ) {
    }

    public record AnswerResultResponse(String attemptId, String questionId, boolean saved) {
    }

    public record SubmitAttemptRequest(
            Map<String, Object> answers,
            OffsetDateTime submittedAt,
            String packageHash,
            String quizVersion,
            Map<String, Object> clientResult,
            List<Map<String, Object>> clientEvents
    ) {
    }

    public record AttemptSubmitResponse(double score, double maxScore, double percent, boolean passed) {
    }

    public record AttemptSyncRequest(
            @NotBlank String packageHash,
            String quizVersion,
            Map<String, Object> attempt,
            List<Map<String, Object>> events,
            Map<String, Object> client
    ) {
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
