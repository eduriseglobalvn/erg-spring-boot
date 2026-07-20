package com.erg.ergspringboot.controller.reporting;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.reporting.ReportingOperations;
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
    private final ReportingOperations reporting;

    public ReportJobController(ReportingOperations reporting) {
        this.reporting = reporting;
    }

    @PostMapping("/assignment-progress")
    public ApiResponse<ReportJobResponse> enqueue(
            @Valid @RequestBody AssignmentProgressJobRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Report queued", reporting.enqueueAssignmentProgress(
                access, body.assignmentId(), body.idempotencyKey()), request);
    }

    @GetMapping("/{jobId}")
    public ApiResponse<ReportJobResponse> status(@PathVariable String jobId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Report status loaded", reporting.status(access, jobId), request);
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
