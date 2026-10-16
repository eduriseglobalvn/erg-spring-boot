package com.erg.ergspringboot.common.response.elearning;

import java.time.OffsetDateTime;
import java.util.List;

public final class StudentDashboardResponses {

    private StudentDashboardResponses() {
    }

    public record StudentDashboardResponse(
            String tenantId,
            StudentDashboardViewerResponse viewer,
            StudentDashboardProfileResponse profile,
            StudentDashboardSummaryResponse summary,
            List<StudentDashboardCourseCardResponse> courses,
            List<StudentDashboardAssignmentCardResponse> assignments,
            List<StudentDashboardNextActionResponse> nextActions,
            List<StudentDashboardAnnouncementCardResponse> announcements,
            String selectedClassId,
            List<StudentDashboardClassMembershipResponse> classes
    ) {
        public StudentDashboardResponse(
                String tenantId,
                StudentDashboardViewerResponse viewer,
                StudentDashboardProfileResponse profile,
                StudentDashboardSummaryResponse summary,
                List<StudentDashboardCourseCardResponse> courses,
                List<StudentDashboardAssignmentCardResponse> assignments,
                List<StudentDashboardNextActionResponse> nextActions,
                List<StudentDashboardAnnouncementCardResponse> announcements
        ) {
            this(
                    tenantId, viewer, profile, summary, courses, assignments, nextActions, announcements,
                    profile == null ? null : profile.academicClassId(), List.of()
            );
        }

        public StudentDashboardResponse(
                String tenantId,
                StudentDashboardViewerResponse viewer,
                StudentDashboardProfileResponse profile,
                StudentDashboardSummaryResponse summary,
                List<StudentDashboardCourseCardResponse> courses,
                List<StudentDashboardAssignmentCardResponse> assignments,
                List<StudentDashboardNextActionResponse> nextActions
        ) {
            this(tenantId, viewer, profile, summary, courses, assignments, nextActions, List.of());
        }
    }

    public record StudentDashboardViewerResponse(
            String userId,
            String tenantId,
            String accountType,
            String accessLevel
    ) {
    }

    public record StudentDashboardProfileResponse(
            String userId,
            String studentId,
            String studentCode,
            String fullName,
            String email,
            String schoolId,
            String academicClassId,
            String status,
            OffsetDateTime updatedAt
    ) {
    }

    public record StudentDashboardClassMembershipResponse(
            String classId,
            String className,
            String schoolId,
            String gradeLabel,
            String academicYear,
            String status
    ) {
    }

    public record StudentDashboardSummaryResponse(
            int enrolledCourseCount,
            int activeAssignmentCount,
            int pendingActionCount,
            StudentDashboardScoreSummaryResponse scoreSummary,
            OffsetDateTime generatedAt
    ) {
    }

    public record StudentDashboardScoreSummaryResponse(
            int completedAttemptCount,
            int attemptedAssignmentCount,
            int passedAttemptCount,
            int averagePercent,
            int bestPercent
    ) {
    }

    public record StudentDashboardCourseCardResponse(
            String id,
            String title,
            String status,
            int progressPercent,
            OffsetDateTime updatedAt
    ) {
    }

    public record StudentDashboardAssignmentCardResponse(
            String id,
            String quizId,
            String title,
            String status,
            OffsetDateTime dueAt
    ) {
    }

    public record StudentDashboardNextActionResponse(
            String id,
            String kind,
            String label,
            String targetId,
            OffsetDateTime dueAt
    ) {
    }

    public record StudentDashboardAnnouncementCardResponse(
            String id,
            String title,
            String content,
            boolean pinned,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt
    ) {
    }
}
