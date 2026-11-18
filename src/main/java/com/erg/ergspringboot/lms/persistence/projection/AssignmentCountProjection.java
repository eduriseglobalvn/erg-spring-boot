package com.erg.ergspringboot.lms.persistence.projection;

/** One count per assignment id; used for the recipient and submitted counts of the homework list. */
public record AssignmentCountProjection(String assignmentId, Long total) {
}
