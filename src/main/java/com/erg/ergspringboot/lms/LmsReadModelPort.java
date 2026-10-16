package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.repository.lms.projection.AssignmentProgressProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Narrow read-model port for LMS workspace projections. */
public interface LmsReadModelPort {
    AssignmentProgressProjection assignmentProgress(String tenantId, String assignmentId);
    StudentGroupWorkspaceResponse studentGroups(String tenantId, String classId, Collection<String> allowedClassIds, String ownerUserId);
    AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(String tenantId, String assignmentId, String attemptStatus, Integer page, Integer size);
    Page<ClassWorkspaceProjection> schoolClassWorkspaces(String tenantId, String schoolId, String status, Pageable pageable);
    ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus,
            Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize);
    ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus,
            Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize,
            boolean includeScoreSummary);
    ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus,
            Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize,
            boolean includeScoreSummary, boolean includeRiskSummary);
    TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(String tenantId, String teacherId, String status, Integer page, Integer size);
}
