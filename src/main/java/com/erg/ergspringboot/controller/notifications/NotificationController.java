package com.erg.ergspringboot.controller.notifications;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final ModuleWorkflowService workflows;

    public NotificationController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return ok("Notifications loaded", workflows.list("notifications", "notifications"), request);
    }

    @PostMapping({"/send", "/batch"})
    public ApiResponse<Map<String, Object>> send(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Notification queued", workflows.job("notifications", last(request), body), request);
    }

    @GetMapping({"/stats", "/channels/status", "/healthz"})
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        return ok("Notification status loaded", workflows.status("notifications", last(request)), request);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(HttpServletRequest request) {
        return ok("Unread count loaded", Map.of("unread", 0), request);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Object>> readAll(HttpServletRequest request) {
        return ok("Notifications marked read", Map.of("read", true, "scope", "all"), request);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id, HttpServletRequest request) {
        return ok("Notification loaded", workflows.get("notifications", "notifications", id), request);
    }

    @PostMapping("/{id}/{action:cancel|resend|read}")
    public ApiResponse<Map<String, Object>> action(@PathVariable String id, @PathVariable String action, HttpServletRequest request) {
        return ok("Notification action accepted", workflows.mark("notifications", "notifications", id, action), request);
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable String id, HttpServletRequest request) {
        return ok("Notification marked read", workflows.mark("notifications", "notifications", id, "read"), request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("Notification deleted", workflows.delete("notifications", "notifications", id), request);
    }

    @GetMapping("/preferences")
    public ApiResponse<Map<String, Object>> preferences(HttpServletRequest request) {
        return ok("Notification preferences loaded", Map.of("email", true, "discord", false, "telegram", false, "whatsapp", false), request);
    }

    @PutMapping("/preferences")
    public ApiResponse<Map<String, Object>> updatePreferences(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Notification preferences updated", workflows.accepted("notifications", "preferences", body), request);
    }

    @PostMapping("/channels/{channel}/test")
    public ApiResponse<Map<String, Object>> testChannel(@PathVariable String channel, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Notification channel tested", workflows.accepted("notifications", channel + "-test", body), request);
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
