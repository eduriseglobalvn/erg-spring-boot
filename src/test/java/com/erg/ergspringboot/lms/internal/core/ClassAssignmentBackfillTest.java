package com.erg.ergspringboot.lms.internal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipient;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassAssignmentBackfillTest {

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentRecipientRepository recipients = mock(AssignmentRecipientRepository.class);
    private final ClassAssignmentBackfill backfill =
            new ClassAssignmentBackfill(assignments, recipients, mock(LmsReadCacheInvalidator.class));

    private Student student() {
        Student s = new Student();
        s.setId("stu-2");
        s.setTenantId("t1");
        s.setAcademicClassId("c1");
        return s;
    }

    private Assignment assignment(String id, String mode, String status, OffsetDateTime due) {
        Assignment a = new Assignment();
        a.setId(id);
        a.setTenantId("t1");
        a.setAcademicClassId("c1");
        a.setRecipientMode(mode);
        a.setStatus(status);
        a.setDueAt(due);
        a.setStudentIds(List.of("stu-1"));
        return a;
    }

    private int run(Assignment... list) {
        when(assignments.findByTenantIdAndAcademicClassIdOrderByCreatedAtDesc("t1", "c1")).thenReturn(List.of(list));
        when(recipients.findFirstByTenantIdAndAssignmentIdAndStudentId(any(), any(), any())).thenReturn(Optional.empty());
        return backfill.grantOpenClassAssignments(student());
    }

    @Test
    void openClassAssignmentsAreGrantedIncludingNoDueDate() {
        Assignment open = assignment("a1", "class", "active", OffsetDateTime.now().plusDays(1));
        Assignment noDue = assignment("a2", "class", "active", null);
        assertEquals(2, run(open, noDue));
        verify(recipients, times(2)).save(any(AssignmentRecipient.class));
        assertEquals(List.of("stu-1", "stu-2"), open.getStudentIds());
    }

    @Test
    void pastDueHandPickedGroupAndInactiveAssignmentsAreSkipped() {
        assertEquals(0, run(
                assignment("a1", "class", "active", OffsetDateTime.now().minusMinutes(1)),
                assignment("a2", "students", "active", null),
                assignment("a3", "group", "active", null),
                assignment("a4", "class", "archived", null),
                assignment("a5", "class", "draft", null)));
        verify(recipients, never()).save(any(AssignmentRecipient.class));
    }

    @Test
    void anExistingRecipientIsNotDuplicated() {
        Assignment open = assignment("a1", "class", "active", null);
        when(assignments.findByTenantIdAndAcademicClassIdOrderByCreatedAtDesc("t1", "c1")).thenReturn(List.of(open));
        when(recipients.findFirstByTenantIdAndAssignmentIdAndStudentId("t1", "a1", "stu-2"))
                .thenReturn(Optional.of(new AssignmentRecipient()));
        assertEquals(0, backfill.grantOpenClassAssignments(student()));
        verify(recipients, never()).save(any(AssignmentRecipient.class));
    }
}
