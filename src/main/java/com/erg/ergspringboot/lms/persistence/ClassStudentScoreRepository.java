package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.projection.ClassScoreBoardAttemptProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassStudentCurrentAssignmentProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassStudentScoreProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    /** Per student, the assignment of their newest attempt in this class, in progress or handed in. */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassStudentCurrentAssignmentProjection(
                attempt.studentId, assignment.id, assignment.title, attempt.status
            )
            from Attempt attempt
            join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and assignment.academicClassId = :academicClassId
              and attempt.studentId in :studentIds
              and attempt.startedAt = (
                  select max(newer.startedAt)
                  from Attempt newer
                  join Assignment newerAssignment
                      on newerAssignment.tenantId = newer.tenantId
                      and newerAssignment.id = newer.assignmentId
                  where newer.tenantId = attempt.tenantId
                    and newer.studentId = attempt.studentId
                    and newerAssignment.academicClassId = :academicClassId
              )
            """)
    List<ClassStudentCurrentAssignmentProjection> findCurrentAssignments(String tenantId, String academicClassId,
            Collection<String> studentIds);

    /** Every handed-in attempt of a class, newest first, for its score board. */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassScoreBoardAttemptProjection(
                attempt.id, attempt.studentId, assignment.id, assignment.title, quiz.id, quiz.title, topic.id, topic.name,
                attempt.score, attempt.maxScore, attempt.percent, attempt.status, attempt.startedAt, attempt.submittedAt
            )
            from Attempt attempt
            join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            left join Quiz quiz
                on quiz.tenantId = attempt.tenantId
                and quiz.id = attempt.quizId
            left join CurriculumTopic topic
                on topic.tenantId = attempt.tenantId
                and topic.id = quiz.categoryId
            where attempt.tenantId = :tenantId
              and assignment.academicClassId = :academicClassId
              and lower(attempt.status) in :completedStates
            order by attempt.submittedAt desc, attempt.id
            """)
    List<ClassScoreBoardAttemptProjection> findClassAttempts(String tenantId, String academicClassId,
            Collection<String> completedStates, Pageable pageable);
}
