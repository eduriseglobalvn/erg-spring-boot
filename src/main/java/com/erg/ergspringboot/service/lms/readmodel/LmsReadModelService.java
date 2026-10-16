package com.erg.ergspringboot.service.lms.readmodel;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.lms.LmsReadModelCompatibility;
import com.erg.ergspringboot.lms.LmsReadModelPort;
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

/**
 * @deprecated Use {@link LmsReadModelPort} through {@code LmsQueryOperations};
 *             this bean is a compatibility adapter.
 */
@Deprecated
@Service
@Transactional(readOnly = true)
public class LmsReadModelService implements LmsReadModelPort {
    private final LmsReadModelPort operations;

    @Autowired
    public LmsReadModelService(LmsReadModelPort operations) {
        this.operations = operations;
    }

    /** Compatibility constructor retained while legacy tests/callers are migrated. */
    @Deprecated
    public LmsReadModelService(AssignmentRepository assignmentRepository,
            AssignmentResourceRepository assignmentResourceRepository,
            AttemptRepository attemptRepository,
            ClassroomRepository classroomRepository,
            StudentRepository studentRepository,
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            ErgReadTaskExecutor readTaskExecutor) {
        this.operations = LmsReadModelCompatibility.fromRepositories(assignmentRepository,
                assignmentResourceRepository, attemptRepository, classroomRepository, studentRepository,
                studentGroupRepository, studentGroupMemberRepository, readTaskExecutor);
    }

    @Override public AssignmentProgressProjection assignmentProgress(String tenantId, String assignmentId) { return operations.assignmentProgress(tenantId, assignmentId); }
    @Override public StudentGroupWorkspaceResponse studentGroups(String tenantId, String classId, Collection<String> allowedClassIds, String ownerUserId) { return operations.studentGroups(tenantId, classId, allowedClassIds, ownerUserId); }
    @Override public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(String tenantId, String assignmentId, String attemptStatus, Integer page, Integer size) { return operations.assignmentProgressWorkspace(tenantId, assignmentId, attemptStatus, page, size); }
    @Override public Page<ClassWorkspaceProjection> schoolClassWorkspaces(String tenantId, String schoolId, String status, Pageable pageable) { return operations.schoolClassWorkspaces(tenantId, schoolId, status, pageable); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize) { return operations.classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary) { return operations.classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary); }
    @Override public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus, Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize, boolean includeScoreSummary, boolean includeRiskSummary) { return operations.classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus, assignmentPage, assignmentSize, includeScoreSummary, includeRiskSummary); }
    @Override public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(String tenantId, String teacherId, String status, Integer page, Integer size) { return operations.teacherHomeworkWorkspace(tenantId, teacherId, status, page, size); }
}
