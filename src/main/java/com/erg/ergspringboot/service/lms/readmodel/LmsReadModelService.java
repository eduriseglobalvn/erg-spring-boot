package com.erg.ergspringboot.service.lms.readmodel;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.lms.internal.readmodel.*;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.lms.*;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade. Production behavior is owned by the LMS internal read-model use cases. */
@Service
@Transactional(readOnly = true)
public class LmsReadModelService {
    private final LmsStudentGroupReadUseCase studentGroups;
    private final LmsAssignmentProgressQueryUseCase assignmentProgress;
    private final LmsClassWorkspaceReadUseCase classWorkspace;
    private final LmsTeacherHomeworkReadUseCase teacherHomework;

    @Autowired
    public LmsReadModelService(LmsStudentGroupReadUseCase studentGroups,
            LmsAssignmentProgressQueryUseCase assignmentProgress,
            LmsClassWorkspaceReadUseCase classWorkspace,
            LmsTeacherHomeworkReadUseCase teacherHomework) {
        this.studentGroups = studentGroups;
        this.assignmentProgress = assignmentProgress;
        this.classWorkspace = classWorkspace;
        this.teacherHomework = teacherHomework;
    }

    /** Compatibility constructor retained while legacy tests/callers are migrated. */
    public LmsReadModelService(AssignmentRepository assignmentRepository,
            AssignmentResourceRepository assignmentResourceRepository,
            AttemptRepository attemptRepository,
            ClassroomRepository classroomRepository,
            StudentRepository studentRepository,
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            ErgReadTaskExecutor readTaskExecutor) {
        this(new LmsStudentGroupReadUseCase(studentGroupRepository, studentGroupMemberRepository),
                new LmsAssignmentProgressQueryUseCase(
                        attemptRepository, assignmentRepository, assignmentResourceRepository),
                new LmsClassWorkspaceReadUseCase(classroomRepository, studentRepository,
                        assignmentRepository, assignmentResourceRepository, attemptRepository),
                new LmsTeacherHomeworkReadUseCase(assignmentRepository, assignmentResourceRepository,
                        classroomRepository, attemptRepository, studentRepository));
    }

    public AssignmentProgressProjection assignmentProgress(String tenantId, String assignmentId) {
        return assignmentProgress.summary(tenantId, assignmentId);
    }

    public StudentGroupWorkspaceResponse studentGroups(String tenantId, String classId,
            Collection<String> allowedClassIds, String ownerUserId) {
        return studentGroups.query(tenantId, classId, allowedClassIds, ownerUserId);
    }

    public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(String tenantId,
            String assignmentId, String attemptStatus, Integer page, Integer size) {
        return assignmentProgress.workspace(tenantId, assignmentId, attemptStatus, page, size);
    }

    public Page<ClassWorkspaceProjection> schoolClassWorkspaces(String tenantId, String schoolId,
            String status, Pageable pageable) {
        return classWorkspace.schoolClasses(tenantId, schoolId, status, pageable);
    }

    public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId,
            String studentStatus, Integer page, Integer size, String assignmentStatus,
            Integer assignmentPage, Integer assignmentSize) {
        return classWorkspace(tenantId, classId, schoolId, studentStatus, page, size,
                assignmentStatus, assignmentPage, assignmentSize, false, false);
    }

    public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId,
            String studentStatus, Integer page, Integer size, String assignmentStatus,
            Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary) {
        return classWorkspace(tenantId, classId, schoolId, studentStatus, page, size,
                assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary, false);
    }

    public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId,
            String studentStatus, Integer page, Integer size, String assignmentStatus,
            Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary,
            boolean includeRiskSummary) {
        return classWorkspace.query(tenantId, classId, schoolId, studentStatus, page, size,
                assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary, includeRiskSummary);
    }

    public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(String tenantId,
            String teacherId, String status, Integer page, Integer size) {
        return teacherHomework.query(tenantId, teacherId, status, page, size);
    }
}
