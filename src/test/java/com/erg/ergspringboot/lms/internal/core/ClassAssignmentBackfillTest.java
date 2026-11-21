package com.erg.ergspringboot.lms.internal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import org.junit.jupiter.api.Test;

class ClassAssignmentBackfillTest {

    private final AssignmentRecipientRepository recipients = mock(AssignmentRecipientRepository.class);
    private final LmsReadCacheInvalidator caches = mock(LmsReadCacheInvalidator.class);
    private final ClassAssignmentBackfill backfill = new ClassAssignmentBackfill(recipients, caches);

    private Student student(String classId) {
        Student s = new Student();
        s.setId("stu-2");
        s.setTenantId("t1");
        s.setAuthUserId("user-2");
        s.setAcademicClassId(classId);
        return s;
    }

    @Test
    void grantsThroughOneRecipientStatementAndEvictsCachesWhenRowsWereInserted() {
        when(recipients.grantOpenClassAssignments("t1", "c1", "stu-2", "user-2")).thenReturn(2);
        assertEquals(2, backfill.grantOpenClassAssignments(student("c1")));
        // Every assignment read except the class workspace is tenant-wide; the class workspace is dropped for that class only.
        verify(caches).evictAfterCommit("t1", LmsReadCacheInvalidator.ASSIGNMENT_READ_CACHES.stream()
                .filter(name -> !"lmsClassWorkspace".equals(name)).toList());
        verify(caches).evictClassWorkspaces("t1", "c1");
    }

    @Test
    void anExistingRecipientIsNotDuplicatedAndEvictsNothing() {
        when(recipients.grantOpenClassAssignments("t1", "c1", "stu-2", "user-2")).thenReturn(0);
        assertEquals(0, backfill.grantOpenClassAssignments(student("c1")));
        verify(caches, never()).evictAfterCommit(org.mockito.ArgumentMatchers.eq("t1"), org.mockito.ArgumentMatchers.anyCollection());
        verify(caches, never()).evictClassWorkspaces(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aStudentWithoutAClassGetsNothing() {
        assertEquals(0, backfill.grantOpenClassAssignments(student(null)));
        verifyNoInteractions(recipients);
    }
}
