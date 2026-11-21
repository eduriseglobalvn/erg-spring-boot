package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.ClassRiskSummaryProjection;
import com.erg.ergspringboot.lms.persistence.projection.OverdueStudentProjection;
import com.erg.ergspringboot.lms.persistence.projection.SchoolStudentStatsProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentRosterProjection;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    long countByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    Optional<Student> findByTenantIdAndId(String tenantId, String id);

    /** A student code is unique inside one school only: two schools both have a class 6A1 with student no. 1 (BUG-A-003). */
    @Query("""
            select student from Student student
            where student.tenantId = :tenantId and coalesce(student.schoolId, '') = coalesce(:schoolId, '')
              and lower(student.studentCode) = lower(:studentCode)
            """)
    Optional<Student> findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase(String tenantId, String schoolId, String studentCode);

    /** Matches on the normalised name (Student.normalizeName) so idx_lms_students_tenant_school_name_normalized (V190) serves it. */
    @Query("""
            select student from Student student
            where student.tenantId = :tenantId and coalesce(student.schoolId, '') = coalesce(:schoolId, '')
              and student.fullNameNormalized = :normalizedName
            """)
    List<Student> findByTenantIdAndSchoolIdAndFullNameNormalized(String tenantId, String schoolId, String normalizedName);

    @Query("select student from Student student where student.tenantId = :tenantId and lower(student.studentCode) = lower(:studentCode)")
    List<Student> findByTenantIdAndStudentCodeIgnoreCase(String tenantId, String studentCode);

    long countByTenantIdAndSchoolId(String tenantId, String schoolId);

    List<Student> findByTenantIdAndSchoolIdOrderByFullNameAsc(String tenantId, String schoolId);

    List<Student> findByTenantIdAndSchoolIdOrderByFullNameAsc(
            String tenantId, String schoolId, Pageable pageable);

    List<Student> findByTenantIdAndAcademicClassIdOrderByFullNameAsc(String tenantId, String academicClassId);

    /** Notification audiences: students with a login who are still on the roll (status vocabulary as in the risk query below). */
    @Query("""
            select student from Student student
            where student.tenantId = :tenantId
              and student.academicClassId in :academicClassIds
              and student.authUserId is not null
              and (student.status is null or lower(student.status) in ('active', 'enrolled'))
            order by student.fullName asc
            """)
    List<Student> findAudienceStudentsInClasses(
            String tenantId, Collection<String> academicClassIds, Pageable pageable);

    @Query("""
            select student from Student student
            where student.tenantId = :tenantId
              and student.authUserId is not null
              and (student.status is null or lower(student.status) in ('active', 'enrolled'))
            order by student.fullName asc
            """)
    List<Student> findAudienceStudents(String tenantId, Pageable pageable);

    @Query("""
            select student from Student student
            where student.tenantId = :tenantId
              and student.academicClassId in :academicClassIds
              and student.authUserId in :authUserIds
              and (student.status is null or lower(student.status) in ('active', 'enrolled'))
            """)
    List<Student> findAudienceStudentsInClassesAmong(
            String tenantId, Collection<String> academicClassIds, Collection<String> authUserIds);

    /** Lookup by code (any case), email (lower-case) or phone with spaces/dots/dashes removed. */
    default List<Student> findAudienceStudentsByKeys(
            String tenantId, Collection<String> codes, Collection<String> emails, Collection<String> phones) {
        return findAudienceStudentsByLowerKeys(tenantId, codes.stream().map(code -> code.toLowerCase(java.util.Locale.ROOT)).toList(),
                emails, phones);
    }

    /** The code index is on lower(student_code) (V164), so codes are matched lower-cased. */
    @Query("""
            select student from Student student
            where student.tenantId = :tenantId
              and student.authUserId is not null
              and (student.status is null or lower(student.status) in ('active', 'enrolled'))
              and (lower(student.studentCode) in :codes
                   or lower(student.email) in :emails
                   or replace(replace(replace(student.phone, ' ', ''), '.', ''), '-', '') in :phones)
            """)
    List<Student> findAudienceStudentsByLowerKeys(
            String tenantId, Collection<String> codes, Collection<String> emails, Collection<String> phones);

    Optional<Student> findFirstByTenantIdAndAuthUserId(String tenantId, String authUserId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.lms.persistence.projection.StudentRosterProjection(
                        student.id,
                        student.schoolId,
                        student.academicClassId,
                        student.studentCode,
                        student.fullName,
                        student.username,
                        student.authUserId,
                        student.email,
                        student.status,
                        student.updatedAt,
                        (select latestAssignment.title from Attempt latest
                            join Assignment latestAssignment
                                on latestAssignment.tenantId = latest.tenantId
                                and latestAssignment.id = latest.assignmentId
                            where latest.tenantId = student.tenantId
                              and latest.studentId = student.id
                              and latestAssignment.academicClassId = :academicClassId
                            order by latest.startedAt desc nulls last, latest.id desc
                            limit 1),
                        (select latest.status from Attempt latest
                            join Assignment latestAssignment
                                on latestAssignment.tenantId = latest.tenantId
                                and latestAssignment.id = latest.assignmentId
                            where latest.tenantId = student.tenantId
                              and latest.studentId = student.id
                              and latestAssignment.academicClassId = :academicClassId
                            order by latest.startedAt desc nulls last, latest.id desc
                            limit 1)
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
    // BUG-C-013: each student's own newest attempt in this class comes from subqueries in the page query, no extra statement.
    Page<StudentRosterProjection> findClassRoster(
            String tenantId,
            String academicClassId,
            String status,
            Pageable pageable
    );

    @Query(
            value = """
                    select new com.erg.ergspringboot.lms.persistence.projection.OverdueStudentProjection(
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
                        and lower(completedAttempt.status) in :completedStates
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
                        and lower(completedAttempt.status) in :completedStates
                    where assignment.tenantId = :tenantId
                      and assignment.assignedBy = :teacherId
                      and lower(assignment.status) = 'active'
                      and assignment.dueAt is not null
                      and assignment.dueAt < CURRENT_TIMESTAMP
                      and (assignment.recipientMode is null or upper(assignment.recipientMode) = 'CLASS')
                      and completedAttempt.id is null
                    """
    )
    Page<OverdueStudentProjection> findTeacherOverdueStudents(String tenantId, String teacherId, Pageable pageable, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default Page<OverdueStudentProjection> findTeacherOverdueStudents(String tenantId, String teacherId, Pageable pageable) {
        return findTeacherOverdueStudents(tenantId, teacherId, pageable, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassRiskSummaryProjection(
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
                and lower(completedAttempt.status) in :completedStates
            left join Attempt lowScoreAttempt
                on lowScoreAttempt.tenantId = student.tenantId
                and lowScoreAttempt.studentId = student.id
                and lower(lowScoreAttempt.status) in :completedStates
                and lowScoreAttempt.percent is not null
                and lowScoreAttempt.percent < :lowScoreThreshold
            where student.tenantId = :tenantId
              and student.academicClassId = :academicClassId
            """)
    ClassRiskSummaryProjection summarizeClassWorkspaceRisks(String tenantId,
            String academicClassId,
            double lowScoreThreshold, java.util.Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default ClassRiskSummaryProjection summarizeClassWorkspaceRisks(String tenantId,
            String academicClassId,
            double lowScoreThreshold) {
        return summarizeClassWorkspaceRisks(tenantId, academicClassId, lowScoreThreshold, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
    /** Shared filter of the school student list; status is the UI group: studying, at-risk or inactive. */
    String SCHOOL_STUDENT_FILTER = """
            from Student student
            left join Classroom classroom on classroom.id = student.academicClassId and classroom.tenantId = student.tenantId
            where student.tenantId = :tenantId
              and student.schoolId = :schoolId
              and (:search is null
                   or lower(student.fullName) like :search
                   or lower(student.studentCode) like :search
                   or student.parentPhone like :search)
              and (:grade is null or classroom.grade = :grade)
              and (:className is null or classroom.name = :className)
              and (:status is null
                   or (:status = 'at-risk' and upper(student.status) = 'AT_RISK')
                   or (:status = 'studying' and upper(student.status) in ('ACTIVE', 'ENROLLED'))
                   or (:status = 'inactive' and (student.status is null
                        or upper(student.status) not in ('AT_RISK', 'ACTIVE', 'ENROLLED'))))
            """;

    @Query(value = "select student " + SCHOOL_STUDENT_FILTER + " order by student.fullName asc, student.id asc",
            countQuery = "select count(student.id) " + SCHOOL_STUDENT_FILTER)
    Page<Student> findSchoolStudents(String tenantId, String schoolId, String search, String grade,
            String className, String status, Pageable pageable);

    /** assignedClassIds: classes with at least one subject; never empty (pass a placeholder id). */
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.SchoolStudentStatsProjection(
                count(student.id),
                coalesce(sum(case when upper(student.status) = 'AT_RISK' then 1 else 0 end), 0),
                coalesce(sum(case when upper(student.status) in ('ACTIVE', 'ENROLLED') then 1 else 0 end), 0),
                coalesce(sum(case when student.studentCode is not null and classroom.name is not null
                    and classroom.grade is not null then 1 else 0 end), 0),
                coalesce(sum(case when student.academicClassId in :assignedClassIds then 1 else 0 end), 0))
            """ + SCHOOL_STUDENT_FILTER)
    SchoolStudentStatsProjection summarizeSchoolStudents(String tenantId, String schoolId, String search, String grade,
            String className, String status, Collection<String> assignedClassIds);

    @Query("""
            select student.academicClassId, count(student.id) from Student student
            where student.tenantId = :tenantId and student.schoolId = :schoolId and student.academicClassId is not null
            group by student.academicClassId
            """)
    List<Object[]> countSchoolStudentsByClass(String tenantId, String schoolId);

    /** Row 0: [number of students with a score, their average score or null]. */
    @Query(value = """
            select count(*), avg(cast(metrics ->> 'averageScore' as double precision))
            from lms_students
            where tenant_id = :tenantId and school_id = :schoolId and metrics ->> 'averageScore' is not null
            """, nativeQuery = true)
    List<Object[]> scoreSchoolStudents(String tenantId, String schoolId);
}
