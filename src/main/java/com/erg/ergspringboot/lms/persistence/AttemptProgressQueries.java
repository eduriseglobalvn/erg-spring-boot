package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.AssignmentProgressProjection;
import org.springframework.data.jpa.repository.Query;

/** Assignment progress counts for {@link AttemptRepository}; split out to keep that repository under its size ceiling. */
public interface AttemptProgressQueries {

    /**
     * Counts students, not attempts: a student counts as handed in by the latest handed-in attempt (any of the
     * completed states), awaiting review when that latest one is still in review, and in progress only while they
     * have not handed in at all.
     */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AssignmentProgressProjection(
                count(distinct case when lower(attempt.status) in :completedStates then attempt.studentId end),
                count(distinct case when lower(attempt.status) = 'in_progress' then attempt.studentId end),
                count(distinct case when lower(attempt.status) in ('needs_review', 'review_required') then attempt.studentId end)
            )
            from Attempt attempt
            where attempt.tenantId = :tenantId
              and attempt.assignmentId = :assignmentId
              and (
                  (lower(attempt.status) = 'in_progress'
                      and not exists (select handedIn.id from Attempt handedIn
                          where handedIn.tenantId = attempt.tenantId and handedIn.assignmentId = attempt.assignmentId
                            and handedIn.studentId = attempt.studentId and lower(handedIn.status) in :completedStates))
                  or (lower(attempt.status) in :completedStates
            """ + LatestHandedInAttempt.OF_ATTEMPT + """
                  )
              )
            """)
    AssignmentProgressProjection summarizeAssignmentProgress(String tenantId, String assignmentId,
            java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default AssignmentProgressProjection summarizeAssignmentProgress(String tenantId, String assignmentId) {
        return summarizeAssignmentProgress(tenantId, assignmentId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
}
