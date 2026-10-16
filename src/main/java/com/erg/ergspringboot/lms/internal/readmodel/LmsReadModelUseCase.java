package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.lms.LmsReadModelPort;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import java.util.Collection;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Composes the LMS read-model use cases behind a stable module port. */
@Service
@Primary
public class LmsReadModelUseCase implements LmsReadModelPort {
    private final LmsStudentGroupReadUseCase studentGroups;
    private final LmsAssignmentProgressQueryUseCase assignmentProgress;
    private final LmsClassWorkspaceReadUseCase classWorkspace;
    private final LmsTeacherHomeworkReadUseCase teacherHomework;

    public LmsReadModelUseCase(LmsStudentGroupReadUseCase studentGroups,
            LmsAssignmentProgressQueryUseCase assignmentProgress,
            LmsClassWorkspaceReadUseCase classWorkspace,
            LmsTeacherHomeworkReadUseCase teacherHomework) {
        this.studentGroups = studentGroups;
        this.assignmentProgress = assignmentProgress;
        this.classWorkspace = classWorkspace;
        this.teacherHomework = teacherHomework;
    }

    @Override public AssignmentProgressProjection assignmentProgress(String tenantId, String assignmentId) { return assignmentProgress.summary(tenantId, assignmentId); }
    @Override public StudentGroupWorkspaceResponse studentGroups(String tenantId, String classId, Collection<String> allowedClassIds, String ownerUserId) { return studentGroups.query(tenantId, classId, allowedClassIds, ownerUserId); }
    @Override public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(String tenantId, String assignmentId, String attemptStatus, Integer page, Integer size) { return assignmentProgress.workspace(tenantId, assignmentId, attemptStatus, page, size); }
    @Override public Page<ClassWorkspaceProjection> schoolClassWorkspaces(String tenantId, String schoolId, String status, Pageable pageable) { return classWorkspace.schoolClasses(tenantId, schoolId, status, pageable); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize) { return classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize, false, false); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary) { return classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary, false); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary, boolean includeRiskSummary) { return classWorkspace.query(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary, includeRiskSummary); }
    @Override public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(String tenantId, String teacherId, String status, Integer page, Integer size) { return teacherHomework.query(tenantId, teacherId, status, page, size); }
}
