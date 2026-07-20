package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.projection.ClassRiskSummaryProjection;
import com.erg.ergspringboot.repository.lms.projection.OverdueStudentProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentRosterProjection;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    Optional<Student> findByTenantIdAndId(String tenantId, String id);

    long countByTenantIdAndSchoolId(String tenantId, String schoolId);

    List<Student> findByTenantIdAndSchoolIdOrderByFullNameAsc(String tenantId, String schoolId);

    List<Student> findByTenantIdAndSchoolIdOrderByFullNameAsc(
            String tenantId, String schoolId, Pageable pageable);

    List<Student> findByTenantIdAndAcademicClassIdOrderByFullNameAsc(String tenantId, String academicClassId);

    Optional<Student> findFirstByTenantIdAndAuthUserId(String tenantId, String authUserId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.StudentRosterProjection(
                        student.id,
                        student.schoolId,
                        student.academicClassId,
                        student.studentCode,
                        student.fullName,
                        student.username,
                        student.authUserId,
                        student.email,
                        student.status,
                        student.updatedAt
                    )
                    from Student student
                    where student.tenantId = :tenantId
                      and student.academicClassId = :academicClassId
                      and (:status is null or lower(student.status) = :status)
                    order by student.fullName asc, student.studentCode asc
                    """,
            countQuery = """
                    select count(student.id)
                    from Student student
                    where student.tenantId = :tenantId
                      and student.academicClassId = :academicClassId
                      and (:status is null or lower(student.status) = :status)
                    """
    )
    Page<StudentRosterProjection> findClassRoster(
            String tenantId,
            String academicClassId,
            String status,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.OverdueStudentProjection(
                        assignment.id,
                        assignment.academicClassId,
                        assignment.quizId,
                        assignment.subjectId,
                        assignment.dueAt,
                        student.id,
                        student.studentCode,
                        student.fullName,
                        student.status,
                        student.updatedAt
                    )
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    left join Attempt completedAttempt
                        on completedAttempt.tenantId = assignment.tenantId
                        and completedAttempt.assignmentId = assignment.id
                        and completedAttempt.studentId = student.id
                        and lower(completedAttempt.status) in ('submitted', 'graded', 'completed')
                    where assignment.tenantId = :tenantId
                      and assignment.assignedBy = :teacherId
                      and lower(assignment.status) = 'active'
                      and assignment.dueAt is not null
                      and assignment.dueAt < CURRENT_TIMESTAMP
                      and (assignment.recipientMode is null or upper(assignment.recipientMode) = 'CLASS')
                      and completedAttempt.id is null
                    order by assignment.dueAt asc, student.fullName asc, student.studentCode asc
                    """,
            countQuery = """
                    select count(student.id)
                    from Assignment assignment
                    join Student student
                        on student.tenantId = assignment.tenantId
                        and student.academicClassId = assignment.academicClassId
                    left join Attempt completedAttempt
                        on completedAttempt.tenantId = assignment.tenantId
                        and completedAttempt.assignmentId = assignment.id
                        and completedAttempt.studentId = student.id
                        and lower(completedAttempt.status) in ('submitted', 'graded', 'completed')
                    where assignment.tenantId = :tenantId
                      and assignment.assignedBy = :teacherId
                      and lower(assignment.status) = 'active'
                      and assignment.dueAt is not null
                      and assignment.dueAt < CURRENT_TIMESTAMP
                      and (assignment.recipientMode is null or upper(assignment.recipientMode) = 'CLASS')
                      and completedAttempt.id is null
                    """
    )
    Page<OverdueStudentProjection> findTeacherOverdueStudents(String tenantId, String teacherId, Pageable pageable);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.ClassRiskSummaryProjection(
                count(distinct case when overdueAssignment.id is not null
                    and completedAttempt.id is null
                    then student.id else null end),
                count(distinct case when lowScoreAttempt.id is not null
                    then student.id else null end),
                count(distinct case when lower(coalesce(student.status, '')) not in ('active', 'enrolled')
                    then student.id else null end)
            )
            from Student student
            left join Assignment overdueAssignment
                on overdueAssignment.tenantId = student.tenantId
                and overdueAssignment.academicClassId = student.academicClassId
                and lower(overdueAssignment.status) = 'active'
                and overdueAssignment.dueAt is not null
                and overdueAssignment.dueAt < CURRENT_TIMESTAMP
            left join Attempt completedAttempt
                on completedAttempt.tenantId = student.tenantId
                and completedAttempt.assignmentId = overdueAssignment.id
                and completedAttempt.studentId = student.id
                and lower(completedAttempt.status) in ('submitted', 'graded', 'completed')
            left join Attempt lowScoreAttempt
                on lowScoreAttempt.tenantId = student.tenantId
                and lowScoreAttempt.studentId = student.id
                and lower(lowScoreAttempt.status) in ('submitted', 'graded', 'completed')
                and lowScoreAttempt.percent is not null
                and lowScoreAttempt.percent < :lowScoreThreshold
            where student.tenantId = :tenantId
              and student.academicClassId = :academicClassId
            """)
    ClassRiskSummaryProjection summarizeClassWorkspaceRisks(
            String tenantId,
            String academicClassId,
            double lowScoreThreshold
    );
}
