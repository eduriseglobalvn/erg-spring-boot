package com.erg.ergspringboot.controller.ai;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping("/api/v1/ai-content")
public class AiContentController {

    private final ModuleWorkflowService workflows;

    public AiContentController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping({"/generate", "/refine"})
    public ApiResponse<Map<String, Object>> generate(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("AI content job queued", workflows.job("ai-content", last(request), body), request);
    }

    @GetMapping("/status/{jobId}")
    public ApiResponse<Map<String, Object>> status(@PathVariable String jobId, HttpServletRequest request) {
        return ok("AI content status loaded", workflows.get("ai-content", "jobs", jobId), request);
    }

    @GetMapping({"/history", "/templates", "/provider-health", "/keys/my", "/keys/dashboard"})
    public ApiResponse<Map<String, Object>> reads(HttpServletRequest request) {
        return ok("AI content data loaded", workflows.status("ai-content", last(request)), request);
    }

    @PostMapping("/keys")
    public ApiResponse<Map<String, Object>> createKey(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> safe = Map.of("provider", body.getOrDefault("provider", "default"), "masked", true, "secretPreview", "****");
        return ok("AI key saved", workflows.create("ai-content", "keys", safe), request);
    }

    @DeleteMapping("/keys/{id}")
    public ApiResponse<Map<String, Object>> deleteKey(@PathVariable String id, HttpServletRequest request) {
        return ok("AI key deleted", workflows.delete("ai-content", "keys", id), request);
    }

    @PostMapping("/keys/{id}/{action:test|select|reactivate}")
    public ApiResponse<Map<String, Object>> keyAction(@PathVariable String id, @PathVariable String action, HttpServletRequest request) {
        return ok("AI key action accepted", Map.of("id", id, "action", action, "secretLeaked", false), request);
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
