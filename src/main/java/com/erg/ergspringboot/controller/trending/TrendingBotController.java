package com.erg.ergspringboot.controller.trending;

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
@RequestMapping("/api/v1")
public class TrendingBotController {

    private final ModuleWorkflowService workflows;

    public TrendingBotController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping({"/trending/healthz", "/trending/ready", "/trending/topics", "/trending/news", "/trending/snapshots", "/trending/feeds", "/trending/discovery-feed", "/trending/history", "/trending/sources", "/trending/stats"})
    public ApiResponse<Map<String, Object>> trendingReads(HttpServletRequest request) {
        return ok("Trending data loaded", workflows.status("trending", last(request)), request);
    }

    @GetMapping("/trending/topics/{topic}")
    public ApiResponse<Map<String, Object>> topic(@PathVariable String topic, HttpServletRequest request) {
        return ok("Trending topic loaded", Map.of("topic", topic, "score", 0), request);
    }

    @PostMapping({"/trending/refresh", "/trending/discover"})
    public ApiResponse<Map<String, Object>> trendingJob(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Trending job queued", workflows.job("trending", last(request), body), request);
    }

    @GetMapping("/bot/conversations")
    public ApiResponse<List<Map<String, Object>>> conversations(HttpServletRequest request) {
        return ok("Bot conversations loaded", workflows.list("bot", "conversations"), request);
    }

    @GetMapping("/bot/conversations/{id}")
    public ApiResponse<Map<String, Object>> conversation(@PathVariable String id, HttpServletRequest request) {
        return ok("Bot conversation loaded", workflows.get("bot", "conversations", id), request);
    }

    @PostMapping("/bot/conversations/{id}/send")
    public ApiResponse<Map<String, Object>> send(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Bot message queued", workflows.job("bot", "send:" + id, body), request);
    }

    @GetMapping("/bot/conversations/{id}/wizard")
    public ApiResponse<Map<String, Object>> wizard(@PathVariable String id, HttpServletRequest request) {
        return ok("Bot wizard loaded", Map.of("conversationId", id, "steps", List.of("input", "confirm", "run")), request);
    }

    @PostMapping("/bot/link")
    public ApiResponse<Map<String, Object>> link(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Bot link created", workflows.create("bot", "links", body), request);
    }

    @GetMapping("/bot/link/{code}")
    public ApiResponse<Map<String, Object>> linkCode(@PathVariable String code, HttpServletRequest request) {
        return ok("Bot link loaded", Map.of("code", code, "valid", true), request);
    }

    @GetMapping("/bot/accounts")
    public ApiResponse<List<Map<String, Object>>> accounts(HttpServletRequest request) {
        return ok("Bot accounts loaded", workflows.list("bot", "accounts"), request);
    }

    @DeleteMapping("/bot/accounts/{id}")
    public ApiResponse<Map<String, Object>> deleteAccount(@PathVariable String id, HttpServletRequest request) {
        return ok("Bot account deleted", workflows.delete("bot", "accounts", id), request);
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
