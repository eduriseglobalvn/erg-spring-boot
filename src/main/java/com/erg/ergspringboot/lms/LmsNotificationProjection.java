package com.erg.ergspringboot.lms;

import java.util.List;
import java.util.Optional;

/** Public, persistence-free audience lookup for notifications triggered by LMS events. */
public interface LmsNotificationProjection {

    Optional<AssignmentAudience> assignmentAudience(String tenantId, String assignmentId);

    /** Same lookup without the recipient list, for events that need only the title and the assigning teacher. */
    Optional<AssignmentSummary> assignmentSummary(String tenantId, String assignmentId);

    Optional<Submitter> submitter(String tenantId, String studentId);

    /** {@code studentUserIds} holds only recipients linked to a login account. */
    record AssignmentAudience(
            String assignmentId,
            String title,
            String classId,
            String assignedBy,
            List<String> studentUserIds
    ) {
        public AssignmentAudience {
            studentUserIds = studentUserIds == null ? List.of() : List.copyOf(studentUserIds);
        }
    }

    record AssignmentSummary(String assignmentId, String title, String classId, String assignedBy) {
    }

    /** {@code userId} is the student's login account ({@code users.id}); null when none is linked. */
    record Submitter(String fullName, String userId) {
    }
}
