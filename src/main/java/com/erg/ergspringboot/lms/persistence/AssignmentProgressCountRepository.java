package com.erg.ergspringboot.lms.persistence;

import com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Per-assignment progress counts, kept out of {@link AssignmentRepository} to hold its size ceiling. */
public interface AssignmentProgressCountRepository extends Repository<Assignment, String> {

    // Recipient counts for a list of assignments (BUG-A-054): saved recipient rows first, class roster when a class assignment has none.
    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection(
                recipient.assignmentId, count(distinct recipient.studentId))
            from AssignmentRecipient recipient
            where recipient.tenantId = :tenantId
              and recipient.assignmentId in :assignmentIds
            group by recipient.assignmentId
            """)
    List<AssignmentCountProjection> countSavedRecipients(String tenantId, Collection<String> assignmentIds);

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection(
                assignment.id, count(student.id))
            from Assignment assignment
            join Student student
                on student.tenantId = assignment.tenantId
                and student.academicClassId = assignment.academicClassId
            where assignment.tenantId = :tenantId
              and assignment.id in :assignmentIds
              and lower(coalesce(assignment.recipientMode, 'class')) = 'class'
              and not exists (
                  select 1 from AssignmentRecipient anyRecipient
                  where anyRecipient.tenantId = assignment.tenantId
                    and anyRecipient.assignmentId = assignment.id)
            group by assignment.id
            """)
    List<AssignmentCountProjection> countClassRosterRecipients(String tenantId, Collection<String> assignmentIds);

    @Query("""
            select new com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection(
                assignment.id, count(distinct attempt.studentId))
            from Assignment assignment
            join Attempt attempt
                on attempt.tenantId = assignment.tenantId
                and attempt.assignmentId = assignment.id
                and lower(attempt.status) in :completedStates
            join Student student
                on student.tenantId = assignment.tenantId
                and student.id = attempt.studentId
            where assignment.tenantId = :tenantId
              and assignment.id in :assignmentIds
            """ + AssignmentRecipientScope.RECIPIENT_OF_ASSIGNMENT + """
            group by assignment.id
            """)
    List<AssignmentCountProjection> countSubmittedRecipients(String tenantId, Collection<String> assignmentIds, Collection<String> completedStates);

    /** Uses the attempt state vocabulary owned by {@code LmsStatePolicy}. */
    default List<AssignmentCountProjection> countSubmittedRecipients(String tenantId, Collection<String> assignmentIds) {
        return countSubmittedRecipients(tenantId, assignmentIds, com.erg.ergspringboot.lms.LmsStatePolicy.completedAttemptStates());
    }
}
