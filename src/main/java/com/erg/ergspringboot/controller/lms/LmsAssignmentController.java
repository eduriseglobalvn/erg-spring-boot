package com.erg.ergspringboot.controller.lms;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.CreateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateRecipientsRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.CreateAssignmentResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.StudentGroupResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.LmsOperations;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentCreate;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentUpdate;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentAssign;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentDelete;
import com.erg.ergspringboot.lms.authorization.LmsStudentGroupCreate;
import com.erg.ergspringboot.lms.authorization.LmsStudentGroupUpdate;
import com.erg.ergspringboot.lms.authorization.LmsStudentGroupDelete;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * LMS distribution writes (spec §4.8). Multi-quiz assignments with flat recipients resolved from a mode
 * (class / group / students); each quiz pins its active published version. Create is idempotent on
 * {@code X-Idempotency-Key}. Reads stay on the existing GraphQL workspaces (re-backed in §4.3).
 */
@RestController
public class LmsAssignmentController {

    private final LmsOperations operations;

    public LmsAssignmentController(LmsOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/api/v1/lms/assignments")
    @LmsAssignmentCreate
    public ApiResponse<CreateAssignmentResponse> create(
            @RequestBody CreateAssignmentRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Assignment created",
                operations.createAssignment(access.tenantId(), access.userId(), idempotencyKey, body), request);
    }

    @PatchMapping("/api/v1/lms/assignments/{assignmentId}")
    @LmsAssignmentUpdate
    public ApiResponse<Void> update(
            @PathVariable String assignmentId,
            @RequestBody UpdateAssignmentRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        operations.updateAssignment(access.tenantId(), assignmentId, body);
        return ok("Assignment updated", null, request);
    }

    @PutMapping("/api/v1/lms/assignments/{assignmentId}/recipients")
    @LmsAssignmentAssign
    public ApiResponse<CreateAssignmentResponse> updateRecipients(
            @PathVariable String assignmentId,
            @RequestBody UpdateRecipientsRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Recipients updated",
                operations.updateAssignmentRecipients(access.tenantId(), assignmentId, body), request);
    }

    @DeleteMapping("/api/v1/lms/assignments/{assignmentId}")
    @LmsAssignmentDelete
    public ApiResponse<Void> delete(@PathVariable String assignmentId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        operations.deleteAssignment(access.tenantId(), assignmentId);
        return ok("Assignment deleted", null, request);
    }

    @PostMapping("/api/v1/lms/student-groups")
    @LmsStudentGroupCreate
    public ApiResponse<StudentGroupResponse> createGroup(
            @RequestBody StudentGroupRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Student group created",
                operations.createStudentGroup(access.tenantId(), access.userId(), body), request);
    }

    @PutMapping("/api/v1/lms/student-groups/{groupId}")
    @LmsStudentGroupUpdate
    public ApiResponse<StudentGroupResponse> updateGroup(
            @PathVariable String groupId,
            @RequestBody StudentGroupRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Student group updated",
                operations.updateStudentGroup(access.tenantId(), groupId, body), request);
    }

    @DeleteMapping("/api/v1/lms/student-groups/{groupId}")
    @LmsStudentGroupDelete
    public ApiResponse<Void> deleteGroup(@PathVariable String groupId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        operations.deleteStudentGroup(access.tenantId(), groupId);
        return ok("Student group deleted", null, request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
