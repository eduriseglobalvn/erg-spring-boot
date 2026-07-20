package com.erg.ergspringboot.controller.reporting;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import com.erg.ergspringboot.service.reporting.ReportJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/jobs")
public class ReportJobController {
    private final ReportJobService reportJobService;
    private final LmsAssignmentService assignmentService;

    public ReportJobController(ReportJobService reportJobService, LmsAssignmentService assignmentService) {
        this.reportJobService = reportJobService;
        this.assignmentService = assignmentService;
    }

    @PostMapping("/assignment-progress")
    public ApiResponse<ReportJobResponse> enqueue(
            @Valid @RequestBody AssignmentProgressJobRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        Assignment assignment = assignmentService.findById(body.assignmentId())
                .filter(item -> access.tenantId().equals(item.getTenantId()))
                .orElseThrow(() -> new ForbiddenException("assignment is outside the current tenant"));
        if (!access.canAccessClass(assignment.getAcademicClassId())) {
            throw new ForbiddenException("assignment is outside the current class scope");
        }
        return ok("Report queued", reportJobService.enqueueAssignmentProgress(
                access.tenantId(), access.userId(), body.assignmentId(), body.idempotencyKey()), request);
    }

    @GetMapping("/{jobId}")
    public ApiResponse<ReportJobResponse> status(@PathVariable String jobId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Report status loaded", reportJobService.get(access.tenantId(), jobId), request);
    }

    public record AssignmentProgressJobRequest(@NotBlank String assignmentId, String idempotencyKey) { }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? request.getHeader("X-Request-ID") : value.toString();
    }
}
