package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.ClassRiskSummaryProjection;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Class risk counters as three independent EXISTS per student, so no join multiplies the rows (was a four-way cartesian join). */
public interface ClassRiskSummaryRepository extends Repository<Student, String> {

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.ClassRiskSummaryProjection(
                count(case when exists (
                    select 1 from Assignment overdue
                    where overdue.tenantId = student.tenantId
                      and overdue.academicClassId = student.academicClassId
                      and lower(overdue.status) = 'active'
                      and overdue.dueAt is not null
                      and overdue.dueAt < CURRENT_TIMESTAMP
                      and not exists (
                          select 1 from Attempt done
                          where done.tenantId = student.tenantId
                            and done.assignmentId = overdue.id
                            and done.studentId = student.id
                            and lower(done.status) in :completedStates)) then 1 else null end),
                count(case when exists (
                    select 1 from Attempt low
                    where low.tenantId = student.tenantId
                      and low.studentId = student.id
                      and lower(low.status) in :completedStates
                      and low.percent is not null
                      and low.percent < :lowScoreThreshold) then 1 else null end),
                count(case when lower(coalesce(student.status, '')) not in ('active', 'enrolled') then 1 else null end)
            )
            from Student student
            where student.tenantId = :tenantId
              and student.academicClassId = :academicClassId
            """)
    ClassRiskSummaryProjection summarizeClassWorkspaceRisks(@Param("tenantId") String tenantId,
            @Param("academicClassId") String academicClassId, @Param("lowScoreThreshold") double lowScoreThreshold,
            @Param("completedStates") Collection<String> completedStates);

    default ClassRiskSummaryProjection summarizeClassWorkspaceRisks(String tenantId, String academicClassId, double lowScoreThreshold) {
        return summarizeClassWorkspaceRisks(tenantId, academicClassId, lowScoreThreshold,
                com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
}
