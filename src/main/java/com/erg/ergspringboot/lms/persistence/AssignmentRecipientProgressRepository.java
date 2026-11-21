package com.erg.ergspringboot.lms.persistence;

import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.COUNT;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.FROM_ROSTER;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.FROM_ROWS;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.FROM_STUDENTS;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.MISSING_LATE_FILTER;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.MISSING_SELECT;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.ORDER;
import static com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressSql.RECIPIENT_SELECT;

import com.erg.ergspringboot.lms.persistence.projection.MissingLateStudentProjection;
import com.erg.ergspringboot.lms.persistence.projection.RecipientProgressProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Assignment progress recipient lists. Saved recipient rows win; an assignment without rows falls back to its class roster
 * (BUG-D-003). The caller picks the variant with one exists check, so neither query ever scans the tenant's students.
 */
public interface AssignmentRecipientProgressRepository extends Repository<AssignmentRecipient, String> {

    @Query(value = RECIPIENT_SELECT + FROM_ROWS + ORDER, countQuery = COUNT + FROM_ROWS)
    Page<RecipientProgressProjection> recipientsFromRows(@Param("tenantId") String tenantId,
            @Param("assignmentId") String assignmentId, Pageable pageable);

    @Query(value = RECIPIENT_SELECT + FROM_ROSTER + ORDER, countQuery = COUNT + FROM_ROSTER)
    Page<RecipientProgressProjection> recipientsFromRoster(@Param("tenantId") String tenantId,
            @Param("assignmentId") String assignmentId, Pageable pageable);

    @Query(value = MISSING_SELECT + FROM_ROWS + MISSING_LATE_FILTER + ORDER, countQuery = COUNT + FROM_ROWS + MISSING_LATE_FILTER)
    Page<MissingLateStudentProjection> missingLateFromRows(@Param("tenantId") String tenantId,
            @Param("assignmentId") String assignmentId, Pageable pageable, @Param("completedStates") Collection<String> completedStates);

    @Query(value = MISSING_SELECT + FROM_ROSTER + MISSING_LATE_FILTER + ORDER, countQuery = COUNT + FROM_ROSTER + MISSING_LATE_FILTER)
    Page<MissingLateStudentProjection> missingLateFromRoster(@Param("tenantId") String tenantId,
            @Param("assignmentId") String assignmentId, Pageable pageable, @Param("completedStates") Collection<String> completedStates);

    /** Missing/late rows restricted to the given students: the flags of one recipients page. */
    @Query(MISSING_SELECT + FROM_STUDENTS + MISSING_LATE_FILTER)
    List<MissingLateStudentProjection> missingLateOf(@Param("tenantId") String tenantId, @Param("assignmentId") String assignmentId,
            @Param("studentIds") Collection<String> studentIds, @Param("completedStates") Collection<String> completedStates);

}
