package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardMemberProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardScoreProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Reads of the class leaderboard ("Xếp hạng"); one grouped query per class, never one per student. */
public interface ClassLeaderboardRepository extends Repository<Attempt, String> {

    /** Same attempt filter as {@code AttemptRepository.summarizeStudentDashboardScores}, grouped by student. */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardScoreProjection(
                attempt.studentId,
                count(distinct attempt.assignmentId),
                avg(attempt.percent)
            )
            from Attempt attempt
            left join Assignment assignment
                on assignment.tenantId = attempt.tenantId
                and assignment.id = attempt.assignmentId
            where attempt.tenantId = :tenantId
              and (attempt.classId = :academicClassId
                   or (attempt.classId is null and assignment.academicClassId = :academicClassId))
              and lower(attempt.status) in :completedStates
            """ + LatestHandedInAttempt.OF_ATTEMPT + """
            group by attempt.studentId
            """)
    List<ClassLeaderboardScoreProjection> summarizeClassScores(
            String tenantId, String academicClassId, Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default List<ClassLeaderboardScoreProjection> summarizeClassScores(String tenantId, String academicClassId) {
        return summarizeClassScores(tenantId, academicClassId, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }

    /** Students on the roll of a class: legacy primary class or an ENROLLED enrollment (as the student dashboard). */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassLeaderboardMemberProjection(
                student.id, student.fullName, student.authUserId
            )
            from Student student
            where student.tenantId = :tenantId
              and (student.status is null or lower(student.status) in ('active', 'enrolled'))
              and (student.academicClassId = :academicClassId
                   or exists (select enrollment.id from StudentEnrollment enrollment
                              where enrollment.tenantId = student.tenantId and enrollment.studentId = student.id
                                and enrollment.classId = :academicClassId and upper(enrollment.status) = 'ENROLLED'))
            """)
    List<ClassLeaderboardMemberProjection> findClassMembers(String tenantId, String academicClassId);

    @Query("""
            select classroom.leaderboardEnabled from Classroom classroom
            where classroom.tenantId = :tenantId and classroom.id = :academicClassId
            """)
    Optional<Boolean> findLeaderboardEnabled(String tenantId, String academicClassId);
}
