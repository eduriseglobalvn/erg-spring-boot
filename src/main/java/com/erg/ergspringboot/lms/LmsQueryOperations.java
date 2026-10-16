package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.service.lms.readmodel.LmsReadModelService;
import java.util.List;
import org.springframework.stereotype.Service;

/** Public LMS read boundary for workspace projections. */
@Service
public class LmsQueryOperations {
    private final LmsReadModelPort reads;

    @org.springframework.beans.factory.annotation.Autowired
    public LmsQueryOperations(LmsReadModelPort reads) {
        this.reads = reads;
    }

    /** Compatibility constructor retained for focused legacy tests. */
    @Deprecated
    public LmsQueryOperations(LmsReadModelService reads) {
        this.reads = reads;
    }

    public ClassWorkspaceResponse classWorkspace(String tenantId, String classId, String schoolId, String studentStatus,
            Integer page, Integer size, String assignmentStatus, Integer assignmentPage, Integer assignmentSize,
            boolean includeScoreSummary, boolean includeRiskSummary) {
        return reads.classWorkspace(tenantId, classId, schoolId, studentStatus, page, size, assignmentStatus,
                assignmentPage, assignmentSize, includeScoreSummary, includeRiskSummary);
    }

    public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(String tenantId, String teacherId, String status,
            Integer page, Integer size) {
        return reads.teacherHomeworkWorkspace(tenantId, teacherId, status, page, size);
    }

    public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(String tenantId, String assignmentId,
            String attemptStatus, Integer page, Integer size) {
        return reads.assignmentProgressWorkspace(tenantId, assignmentId, attemptStatus, page, size);
    }

    public StudentGroupWorkspaceResponse studentGroups(String tenantId, String classId, List<String> readableClassIds,
            String userId) {
        return reads.studentGroups(tenantId, classId, readableClassIds, userId);
    }
}
