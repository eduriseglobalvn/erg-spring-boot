package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.persistence.projection.AssignmentCardProjection;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentDetailProjection;
import com.erg.ergspringboot.lms.persistence.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.lms.persistence.projection.RecipientProgressProjection;
import com.erg.ergspringboot.lms.persistence.projection.TeacherHomeworkSummaryProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Narrow read contract for assignment workspaces; no assignment entity escapes it. */
public interface LmsAssignmentQueryPort {
    Optional<AssignmentDetailProjection> findAssignmentDetail(String tenantId, String assignmentId);
    Page<RecipientProgressProjection> findAssignmentRecipients(String tenantId, String assignmentId, Pageable pageable);
    Page<MissingLateStudentProjection> findAssignmentMissingLateStudents(String tenantId, String assignmentId, Pageable pageable);
    Page<AssignmentCardProjection> findTeacherAssignmentCards(String tenantId, String teacherId, String status, Pageable pageable);
    TeacherHomeworkSummaryProjection summarizeTeacherHomeworkWorkspace(String tenantId, String teacherId);
}
