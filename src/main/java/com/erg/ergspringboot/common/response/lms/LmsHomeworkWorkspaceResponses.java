package com.erg.ergspringboot.common.response.lms;

import com.erg.ergspringboot.common.response.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;

public final class LmsHomeworkWorkspaceResponses {

    private LmsHomeworkWorkspaceResponses() {
    }

    public record TeacherHomeworkWorkspaceResponse(
            String tenantId,
            String teacherId,
            TeacherHomeworkSummaryResponse summary,
            java.util.List<ClassOptionResponse> classOptions,
            PageResponse<HomeworkAssignmentCardResponse> assignments,
            PageResponse<PendingReviewResponse> pendingReviews,
            PageResponse<OverdueStudentResponse> overdueStudents
    ) {
    }

    public record StudentGroupWorkspaceResponse(
            String tenantId,
            List<StudentGroupResponse> groups
    ) {
    }

    public record StudentGroupResponse(
            String id,
            String name,
            String classId,
            String note,
            String color,
            String ownerUserId,
            List<String> studentIds,
            int studentCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record TeacherHomeworkSummaryResponse(
            int activeAssignmentCount,
            int overdueAssignmentCount,
            int pendingReviewAssignmentCount
    ) {
    }

    public record HomeworkAssignmentCardResponse(
            String id,
            String academicClassId,
            String quizId,
            String subjectId,
            OffsetDateTime dueAt,
            String status,
            String assignedBy,
            String recipientMode,
            List<AssignmentResourceResponse> resources,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ClassOptionResponse(
            String id,
            String schoolId,
            String name,
            String grade,
            String academicYear,
            String status
    ) {
    }

    public record PendingReviewResponse(
            String attemptId,
            String assignmentId,
            String academicClassId,
            String quizId,
            String subjectId,
            String studentId,
            String studentCode,
            String fullName,
            String status,
            Double score,
            Double maxScore,
            int percent,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record OverdueStudentResponse(
            String assignmentId,
            String academicClassId,
            String quizId,
            String subjectId,
            OffsetDateTime dueAt,
            String studentId,
            String studentCode,
            String fullName,
            String status,
            OffsetDateTime updatedAt
    ) {
    }

    public record AssignmentProgressWorkspaceResponse(
            String tenantId,
            AssignmentDetailResponse assignment,
            AssignmentProgressSummaryResponse summary,
            PageResponse<AssignmentAttemptSummaryResponse> attempts,
            PageResponse<RecipientProgressResponse> recipients,
            PageResponse<MissingLateStudentResponse> missingLateStudents
    ) {
        public AssignmentProgressWorkspaceResponse(
                String tenantId,
                AssignmentDetailResponse assignment,
                AssignmentProgressSummaryResponse summary,
                PageResponse<AssignmentAttemptSummaryResponse> attempts
        ) {
            this(tenantId, assignment, summary, attempts, emptyPage(), emptyPage());
        }

        private static <T> PageResponse<T> emptyPage() {
            return new PageResponse<>(List.of(), 0, 0, 0, 0, false, false);
        }
    }

    public record AssignmentDetailResponse(
            String id,
            String academicClassId,
            String quizId,
            String subjectId,
            OffsetDateTime dueAt,
            String status,
            String assignedBy,
            String recipientMode,
            List<AssignmentResourceResponse> resources,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AssignmentResourceResponse(
            String quizId,
            String quizVersionId,
            String quizVersionLabel,
            int orderIndex
    ) {
    }

    public record AssignmentProgressSummaryResponse(
            int submittedCount,
            int inProgressCount,
            int needsReviewCount
    ) {
    }

    public record AssignmentAttemptSummaryResponse(
            String id,
            String assignmentId,
            String quizId,
            String studentId,
            String status,
            Double score,
            Double maxScore,
            int percent,
            Boolean passed,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record RecipientProgressResponse(
            String studentId,
            String schoolId,
            String academicClassId,
            String studentCode,
            String fullName,
            String username,
            String authUserId,
            String email,
            String status,
            boolean missing,
            boolean late,
            AssignmentAttemptScoreResponse latestAttempt,
            AssignmentAttemptScoreResponse bestAttempt,
            OffsetDateTime updatedAt
    ) {
    }

    public record MissingLateStudentResponse(
            String studentId,
            String schoolId,
            String academicClassId,
            String studentCode,
            String fullName,
            String username,
            String authUserId,
            String email,
            String status,
            OffsetDateTime dueAt,
            boolean missing,
            boolean late,
            OffsetDateTime updatedAt
    ) {
    }

    public record AssignmentAttemptScoreResponse(
            String id,
            String assignmentId,
            String quizId,
            String studentId,
            String status,
            Double score,
            Double maxScore,
            int percent,
            Boolean passed,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
    }
}
