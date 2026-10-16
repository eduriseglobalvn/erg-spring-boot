package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.internal.readmodel.*;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.lms.*;

/** @deprecated Repository construction seam for the legacy read-model adapter. */
@Deprecated
public final class LmsReadModelCompatibility {
    private LmsReadModelCompatibility() { }

    public static LmsReadModelPort fromRepositories(
            AssignmentRepository assignmentRepository,
            AssignmentResourceRepository assignmentResourceRepository,
            AttemptRepository attemptRepository,
            ClassroomRepository classroomRepository,
            StudentRepository studentRepository,
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            ErgReadTaskExecutor readTaskExecutor) {
        return new LmsReadModelUseCase(
                new LmsStudentGroupReadUseCase(studentGroupRepository, studentGroupMemberRepository),
                new LmsAssignmentProgressQueryUseCase(attemptRepository, assignmentRepository, assignmentResourceRepository),
                new LmsClassWorkspaceReadUseCase(classroomRepository, studentRepository, assignmentRepository,
                        assignmentResourceRepository, attemptRepository),
                new LmsTeacherHomeworkReadUseCase(assignmentRepository, assignmentResourceRepository,
                        classroomRepository, attemptRepository, studentRepository));
    }
}
