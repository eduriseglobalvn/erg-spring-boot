package com.erg.ergspringboot.common.response.quiz;

import java.util.List;

/**
 * LMS distribution write response payloads (spec §4.8). Create returns the assignment id plus the
 * resolved flat recipient count and the version-pinned resources, so the FE can confirm exactly who
 * received which quiz version.
 */
public final class AssignmentResponses {

    private AssignmentResponses() {
    }

    public record CreateAssignmentResponse(
            String assignmentId,
            int recipientCount,
            List<AssignmentResourceResponse> resources
    ) {
    }

    public record AssignmentResourceResponse(
            String quizId,
            String quizVersionId,
            String quizVersionLabel
    ) {
    }

    public record StudentGroupResponse(
            String groupId,
            String name,
            String classId,
            String note,
            String color,
            int studentCount
    ) {
    }
}
