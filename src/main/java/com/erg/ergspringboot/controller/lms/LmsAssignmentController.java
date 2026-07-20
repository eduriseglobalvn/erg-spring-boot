package com.erg.ergspringboot.controller.lms;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.CreateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateAssignmentRequest;
import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.UpdateRecipientsRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.CreateAssignmentResponse;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.StudentGroupResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsAssignmentCommandService;
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

    private final LmsAssignmentCommandService commandService;

    public LmsAssignmentController(LmsAssignmentCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/api/v1/lms/assignments")
    public ApiResponse<CreateAssignmentResponse> create(
            @RequestBody CreateAssignmentRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Assignment created",
                commandService.create(access.tenantId(), access.userId(), idempotencyKey, body), request);
    }

    @PatchMapping("/api/v1/lms/assignments/{assignmentId}")
    public ApiResponse<Void> update(
            @PathVariable String assignmentId,
            @RequestBody UpdateAssignmentRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        commandService.updateAssignment(access.tenantId(), assignmentId, body);
        return ok("Assignment updated", null, request);
    }

    @PutMapping("/api/v1/lms/assignments/{assignmentId}/recipients")
    public ApiResponse<CreateAssignmentResponse> updateRecipients(
            @PathVariable String assignmentId,
            @RequestBody UpdateRecipientsRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Recipients updated",
                commandService.updateRecipients(access.tenantId(), assignmentId, body), request);
    }

    @DeleteMapping("/api/v1/lms/assignments/{assignmentId}")
    public ApiResponse<Void> delete(@PathVariable String assignmentId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        commandService.delete(access.tenantId(), assignmentId);
        return ok("Assignment deleted", null, request);
    }

    @PostMapping("/api/v1/lms/student-groups")
    public ApiResponse<StudentGroupResponse> createGroup(
            @RequestBody StudentGroupRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Student group created",
                commandService.createGroup(access.tenantId(), access.userId(), body), request);
    }

    @PutMapping("/api/v1/lms/student-groups/{groupId}")
    public ApiResponse<StudentGroupResponse> updateGroup(
            @PathVariable String groupId,
            @RequestBody StudentGroupRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Student group updated",
                commandService.updateGroup(access.tenantId(), groupId, body), request);
    }

    @DeleteMapping("/api/v1/lms/student-groups/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable String groupId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        commandService.deleteGroup(access.tenantId(), groupId);
        return ok("Student group deleted", null, request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
