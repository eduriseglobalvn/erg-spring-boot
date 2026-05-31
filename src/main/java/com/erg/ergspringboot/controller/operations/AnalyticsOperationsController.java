package com.erg.ergspringboot.controller.operations;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsOperationsController {

    private final ModuleWorkflowService workflows;

    public AnalyticsOperationsController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping({"/insight/session/begin", "/insight/behavior", "/insight/identify", "/insight/firebase/sync"})
    public ApiResponse<Map<String, Object>> insightWrite(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Insight event accepted", workflows.create("analytics", last(request), body), request);
    }

    @PutMapping("/insight/session/{id}/finish")
    public ApiResponse<Map<String, Object>> finishSession(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Insight session finished", workflows.update("analytics", "sessions", id, body), request);
    }

    @GetMapping({"/insight/stats", "/insight/overview", "/insight/posts/summary", "/insight/top-content", "/insight/traffic-sources", "/insight/insights", "/insight/firebase/stats", "/insight/export"})
    public ApiResponse<Map<String, Object>> insightRead(HttpServletRequest request) {
        return ok("Insight data loaded", workflows.status("analytics", last(request)), request);
    }

    @GetMapping({"/insight/user-journey/{sessionId}", "/insight/sessions/{sessionId}"})
    public ApiResponse<Map<String, Object>> insightSession(@PathVariable String sessionId, HttpServletRequest request) {
        return ok("Insight session loaded", Map.of("sessionId", sessionId, "events", List.of()), request);
    }

    @GetMapping("/audit/logs")
    public ApiResponse<List<Map<String, Object>>> auditLogs(HttpServletRequest request) {
        return ok("Audit logs loaded", workflows.list("audit", "logs"), request);
    }

    @GetMapping("/audit/logs/{id}")
    public ApiResponse<Map<String, Object>> auditLog(@PathVariable String id, HttpServletRequest request) {
        return ok("Audit log loaded", workflows.get("audit", "logs", id), request);
    }

    @GetMapping({"/operations/system-status", "/operations/logs", "/operations/firewall/list"})
    public ApiResponse<Map<String, Object>> operationsRead(HttpServletRequest request) {
        return ok("Operations data loaded", workflows.status("operations", last(request)), request);
    }

    @GetMapping("/operations/configs")
    public ApiResponse<List<Map<String, Object>>> configs(HttpServletRequest request) {
        return ok("Operation configs loaded", workflows.list("operations", "configs"), request);
    }

    @PostMapping("/operations/configs")
    public ApiResponse<Map<String, Object>> createConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Operation config saved", workflows.create("operations", "configs", body), request);
    }

    @PutMapping("/operations/configs/{id}")
    public ApiResponse<Map<String, Object>> updateConfig(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Operation config updated", workflows.update("operations", "configs", id, body), request);
    }

    @DeleteMapping("/operations/configs/{id}")
    public ApiResponse<Map<String, Object>> deleteConfig(@PathVariable String id, HttpServletRequest request) {
        return ok("Operation config deleted", workflows.delete("operations", "configs", id), request);
    }

    @PostMapping({"/operations/firewall/block", "/operations/firewall/unblock"})
    public ApiResponse<Map<String, Object>> firewallMutation(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Firewall operation accepted", workflows.accepted("operations", last(request), body), request);
    }

    @GetMapping("/operations/firewall/check/{ip}")
    public ApiResponse<Map<String, Object>> firewallCheck(@PathVariable String ip, HttpServletRequest request) {
        return ok("Firewall check loaded", Map.of("ip", ip, "blocked", false), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }

    private String last(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
