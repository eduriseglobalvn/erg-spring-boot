package com.erg.ergspringboot.reporting;

import java.util.Optional;

/** Reporting's narrow authorization port; persistence types never cross it. */
public interface AssignmentScopeLookup {
    Optional<AssignmentScope> findById(String assignmentId);

    record AssignmentScope(String tenantId, String academicClassId) { }
}
