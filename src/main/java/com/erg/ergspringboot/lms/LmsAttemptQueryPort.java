package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptScoreProjection;
import com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.PendingReviewProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Narrow read contract for attempt workspaces; query projections are immutable. */
public interface LmsAttemptQueryPort {
    AssignmentProgressProjection summarizeAssignmentProgress(String tenantId, String assignmentId);
    Page<AttemptSummaryProjection> findAssignmentAttemptSummaries(String tenantId, String assignmentId, String status, Pageable pageable);
    List<AttemptScoreProjection> findLatestAttemptScoresForRecipients(String tenantId, String assignmentId, Collection<String> studentIds);
    List<AttemptScoreProjection> findBestAttemptScoresForRecipients(String tenantId, String assignmentId, Collection<String> studentIds);
    Page<PendingReviewProjection> findTeacherPendingReviews(String tenantId, String teacherId, Pageable pageable);
}
