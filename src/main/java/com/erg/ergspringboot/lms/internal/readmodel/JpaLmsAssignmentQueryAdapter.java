package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.lms.LmsAssignmentQueryPort;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.AssignmentProgressCountRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentCardProjection;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.lms.persistence.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.lms.persistence.projection.RecipientProgressProjection;
import com.erg.ergspringboot.lms.persistence.projection.TeacherHomeworkSummaryProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Current JPA adapter for the assignment query port. */
@Service
public class JpaLmsAssignmentQueryAdapter implements LmsAssignmentQueryPort {
    private final AssignmentRepository assignments;
    private final AssignmentProgressCountRepository progressCounts;
    private final AssignmentRecipientRepository recipientRows;
    private final AssignmentRecipientProgressRepository recipientProgress;

    public JpaLmsAssignmentQueryAdapter(AssignmentRepository assignments, AssignmentProgressCountRepository progressCounts,
            AssignmentRecipientRepository recipientRows, AssignmentRecipientProgressRepository recipientProgress) {
        this.assignments = assignments;
        this.progressCounts = progressCounts;
        this.recipientRows = recipientRows;
        this.recipientProgress = recipientProgress;
    }

    @Override public Optional<AssignmentDetailProjection> findAssignmentDetail(String tenantId, String assignmentId) { return assignments.findAssignmentDetail(tenantId, assignmentId); }
    @Override public Page<RecipientProgressProjection> findAssignmentRecipients(String tenantId, String assignmentId, Pageable pageable) {
        return hasRows(tenantId, assignmentId) ? recipientProgress.recipientsFromRows(tenantId, assignmentId, pageable)
                : recipientProgress.recipientsFromRoster(tenantId, assignmentId, pageable);
    }
    @Override public Page<MissingLateStudentProjection> findAssignmentMissingLateStudents(
            String tenantId, String assignmentId, Pageable pageable) {
        var states = LmsStatePolicy.completedAttemptStates();
        return hasRows(tenantId, assignmentId) ? recipientProgress.missingLateFromRows(tenantId, assignmentId, pageable, states)
                : recipientProgress.missingLateFromRoster(tenantId, assignmentId, pageable, states);
    }
    @Override public List<MissingLateStudentProjection> findMissingLateOf(
            String tenantId, String assignmentId, Collection<String> studentIds) {
        return studentIds.isEmpty() ? List.of()
                : recipientProgress.missingLateOf(tenantId, assignmentId, studentIds, LmsStatePolicy.completedAttemptStates());
    }
    @Override public Page<AssignmentCardProjection> findTeacherAssignmentCards(String tenantId, String teacherId, String status, Pageable pageable) { return assignments.findTeacherAssignmentCards(tenantId, teacherId, status, pageable); }
    @Override public TeacherHomeworkSummaryProjection summarizeTeacherHomeworkWorkspace(String tenantId, String teacherId) { return assignments.summarizeTeacherHomeworkWorkspace(tenantId, teacherId); }
    @Override public List<AssignmentCountProjection> countSavedRecipients(String tenantId, Collection<String> assignmentIds) { return progressCounts.countSavedRecipients(tenantId, assignmentIds); }
    @Override public List<AssignmentCountProjection> countClassRosterRecipients(String tenantId, Collection<String> assignmentIds) { return progressCounts.countClassRosterRecipients(tenantId, assignmentIds); }
    @Override public List<AssignmentCountProjection> countSubmittedRecipients(String tenantId, Collection<String> assignmentIds) { return progressCounts.countSubmittedRecipients(tenantId, assignmentIds); }

    private boolean hasRows(String tenantId, String assignmentId) { return recipientRows.existsByTenantIdAndAssignmentId(tenantId, assignmentId); }
}
