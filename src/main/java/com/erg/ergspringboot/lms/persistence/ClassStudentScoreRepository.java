package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.projection.ClassStudentScoreProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Per-student scores for the class roster, by the same rule as the student's own dashboard: the score of an
 * assignment is the latest handed-in attempt, the average is the mean of those. Kept out of
 * {@link AttemptRepository} to hold its size ceiling.
 */
public interface ClassStudentScoreRepository extends Repository<Attempt, String> {

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassStudentScoreProjection(
                attempt.studentId,
                count(attempt.id),
                avg(attempt.percent)
            )
            from Attempt attempt
            join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and assignment.academicClassId = :academicClassId
              and attempt.studentId in :studentIds
              and lower(attempt.status) in :completedStates
            """ + LatestHandedInAttempt.OF_ATTEMPT + """
            group by attempt.studentId
            """)
    List<ClassStudentScoreProjection> summarizeClassStudentScores(String tenantId, String academicClassId,
            Collection<String> studentIds, Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default List<ClassStudentScoreProjection> summarizeClassStudentScores(String tenantId, String academicClassId,
            Collection<String> studentIds) {
        if (studentIds.isEmpty()) return List.of();
        return summarizeClassStudentScores(tenantId, academicClassId, studentIds, LmsStatePolicy.completedAttemptStates());
    }
}
