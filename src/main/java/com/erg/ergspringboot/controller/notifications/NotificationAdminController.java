package com.erg.ergspringboot.controller.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminSendResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminStatsResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.service.notifications.NotificationSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {

    private final NotificationAdminOperations operations;
    private final NotificationSecuritySupport security;

    public NotificationAdminController(
            NotificationAdminOperations operations,
            NotificationSecuritySupport security
    ) {
        this.operations = operations;
        this.security = security;
    }

    @PostMapping({"/send", "/broadcast"})
    public ApiResponse<AdminSendResponse> send(@Valid @RequestBody AdminSendRequest body, HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        return ok(
                "Notification queued",
                operations.send(principal.tenantId(), principal.userId(), body),
                request
        );
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats(HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        return ok(
                "Notification stats loaded",
                operations.stats(principal.tenantId()),
                request
        );
    }

    @PostMapping("/outbox/run")
    public ApiResponse<OutboxRunResponse> runOutbox(HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        return ok("Notification outbox processed", operations.runOutbox(principal.tenantId()), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
