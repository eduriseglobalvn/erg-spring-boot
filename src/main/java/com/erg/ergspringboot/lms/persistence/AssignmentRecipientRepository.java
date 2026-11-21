package com.erg.ergspringboot.lms.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRecipientRepository extends JpaRepository<AssignmentRecipient, String> {

    List<AssignmentRecipient> findByAssignmentId(String assignmentId);

    List<AssignmentRecipient> findByTenantIdAndStudentId(String tenantId, String studentId);

    Optional<AssignmentRecipient> findByAssignmentIdAndStudentId(String assignmentId, String studentId);

    Optional<AssignmentRecipient> findFirstByTenantIdAndAssignmentIdAndStudentId(String tenantId, String assignmentId, String studentId);

    long countByAssignmentId(String assignmentId);

    boolean existsByTenantIdAndAssignmentId(String tenantId, String assignmentId);

    /** Gives a joining student the open class-mode assignments of the class; idempotent through uq_assignment_recipients_assignment_student. */
    @Modifying
    @Query(value = """
            with granted as (
                insert into assignment_recipients (id, created_at, updated_at, assignment_id, tenant_id, student_id,
                                                   student_user_id, class_id, source_mode)
                select cast(gen_random_uuid() as varchar), now(), now(), a.id, a.tenant_id, :studentId,
                       cast(:studentUserId as varchar), :classId, 'class'
                from lms_assignments a
                where a.tenant_id = :tenantId and a.academic_class_id = :classId and lower(a.recipient_mode) = 'class'
                  and lower(a.status) in ('active', 'assigned', 'published')
                  and (a.due_at is null or a.due_at > now())
                on conflict (assignment_id, student_id) do nothing
                returning assignment_id)
            update lms_assignments a
               set recipient_count = coalesce(a.recipient_count, 0) + 1, record_version = a.record_version + 1, updated_at = now()
              from granted where a.id = granted.assignment_id
            """, nativeQuery = true)
    int grantOpenClassAssignments(@Param("tenantId") String tenantId, @Param("classId") String classId,
            @Param("studentId") String studentId, @Param("studentUserId") String studentUserId);

    /** One DELETE instead of a select plus one delete per row (20,000 recipients). */
    @Modifying(flushAutomatically = true)
    @Query("delete from AssignmentRecipient r where r.assignmentId = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") String assignmentId);

    /** Size of the legacy student_ids snapshot, counted in SQL so the lazy jsonb attribute is never loaded. */
    @Query(value = "select case when jsonb_typeof(student_ids) = 'array' then jsonb_array_length(student_ids) else 0 end"
            + " from lms_assignments where id = :id", nativeQuery = true)
    int countLegacyStudentIds(@Param("id") String id);

    /** True when the legacy student_ids snapshot names the student id or the login id. */
    @Query(value = "select exists (select 1 from lms_assignments where id = :id"
            + " and (student_ids @> jsonb_build_array(cast(:studentId as text))"
            + " or student_ids @> jsonb_build_array(cast(:authUserId as text))))", nativeQuery = true)
    boolean legacySnapshotNames(@Param("id") String id, @Param("studentId") String studentId,
            @Param("authUserId") String authUserId);

    void deleteByAssignmentIdAndStudentId(String assignmentId, String studentId);
}
