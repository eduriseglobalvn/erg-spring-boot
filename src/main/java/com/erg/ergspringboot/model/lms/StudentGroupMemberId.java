package com.erg.ergspringboot.model.lms;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link StudentGroupMember}: (groupId, studentId). */
public class StudentGroupMemberId implements Serializable {

    private String groupId;
    private String studentId;

    public StudentGroupMemberId() {
    }

    public StudentGroupMemberId(String groupId, String studentId) {
        this.groupId = groupId;
        this.studentId = studentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StudentGroupMemberId that)) {
            return false;
        }
        return Objects.equals(groupId, that.groupId) && Objects.equals(studentId, that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, studentId);
    }
}
