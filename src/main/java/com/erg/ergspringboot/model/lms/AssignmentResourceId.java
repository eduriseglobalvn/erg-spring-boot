package com.erg.ergspringboot.model.lms;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link AssignmentResource}: one quiz per assignment. */
public class AssignmentResourceId implements Serializable {

    private String assignmentId;
    private String quizId;

    public AssignmentResourceId() {
    }

    public AssignmentResourceId(String assignmentId, String quizId) {
        this.assignmentId = assignmentId;
        this.quizId = quizId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssignmentResourceId that)) {
            return false;
        }
        return Objects.equals(assignmentId, that.assignmentId) && Objects.equals(quizId, that.quizId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignmentId, quizId);
    }
}
