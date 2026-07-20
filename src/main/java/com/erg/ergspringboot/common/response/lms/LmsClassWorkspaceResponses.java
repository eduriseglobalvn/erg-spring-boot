package com.erg.ergspringboot.common.response.lms;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentResourceResponse;
import java.time.OffsetDateTime;
import java.util.List;

public final class LmsClassWorkspaceResponses {

    private LmsClassWorkspaceResponses() {
    }

    public record ClassWorkspaceResponse(
            String tenantId,
            ClassInfoResponse classInfo,
            ClassScoreSummaryResponse scoreSummary,
            ClassRiskSummaryResponse riskSummary,
            PageResponse<StudentRosterItemResponse> students,
            PageResponse<AssignmentSummaryItemResponse> assignments
    ) {
    }

    public record ClassScoreSummaryResponse(
            int completedAttemptCount,
            int attemptedStudentCount,
            int averagePercent,
            int bestPercent
    ) {
    }

    public record ClassRiskSummaryResponse(
            int missingOverdueStudentCount,
            int lowScoreStudentCount,
            int inactiveStudentCount
    ) {
    }

    public record ClassInfoResponse(
            String id,
            String schoolId,
            String name,
            String grade,
            String academicYear,
            String status,
            String homeroomTeacherId,
            long studentCount,
            long assignmentCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record StudentRosterItemResponse(
            String id,
            String schoolId,
            String academicClassId,
            String studentCode,
            String fullName,
            String username,
            String authUserId,
            String email,
            String status,
            OffsetDateTime updatedAt
    ) {
    }

    public record AssignmentSummaryItemResponse(
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
}
