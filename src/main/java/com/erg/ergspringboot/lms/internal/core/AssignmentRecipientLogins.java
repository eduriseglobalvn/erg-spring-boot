package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.persistence.AssignmentRecipient;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Login ids of an assignment's recipients, bounded: the scope keys of the per-student read caches. */
public interface AssignmentRecipientLogins extends Repository<AssignmentRecipient, String> {

    @Query("select r.studentUserId from AssignmentRecipient r where r.assignmentId = :assignmentId and r.studentUserId is not null")
    List<String> findLogins(@Param("assignmentId") String assignmentId, Pageable pageable);
}
