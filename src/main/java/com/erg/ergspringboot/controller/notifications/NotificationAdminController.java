package com.erg.ergspringboot.controller.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminSendResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminStatsResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandResult;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import com.erg.ergspringboot.service.notifications.NotificationOutboxWorker;
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

    private final NotificationCommandService commandService;
    private final NotificationOutboxWorker outboxWorker;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationSecuritySupport security;

    public NotificationAdminController(
            NotificationCommandService commandService,
            NotificationOutboxWorker outboxWorker,
            NotificationOutboxRepository outboxRepository,
            NotificationSecuritySupport security
    ) {
        this.commandService = commandService;
        this.outboxWorker = outboxWorker;
        this.outboxRepository = outboxRepository;
        this.security = security;
    }

    @PostMapping({"/send", "/broadcast"})
    public ApiResponse<AdminSendResponse> send(@Valid @RequestBody AdminSendRequest body, HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        NotificationCommandResult result = commandService.enqueue(new NotificationCommand(
                principal.tenantId(),
                principal.userId(),
                body.idempotencyKey(),
                body.eventType(),
                body.sourceType(),
                body.sourceId(),
                body.title(),
                body.bodyPreview(),
                body.actionUrl(),
                body.priority(),
                body.portal(),
                body.userIds(),
                body.topicName(),
                body.data()
        ));
        return ok(
                "Notification queued",
                new AdminSendResponse(result.notificationId(), result.recipientCount(), result.outboxCount()),
                request
        );
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> stats(HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        return ok(
                "Notification stats loaded",
                new AdminStatsResponse(
                        outboxRepository.countByTenantIdAndStatus(principal.tenantId(), "pending"),
                        outboxRepository.countByTenantIdAndStatus(principal.tenantId(), "retry"),
                        outboxRepository.countByTenantIdAndStatus(principal.tenantId(), "sent"),
                        outboxRepository.countByTenantIdAndStatus(principal.tenantId(), "dead")
                ),
                request
        );
    }

    @PostMapping("/outbox/run")
    public ApiResponse<OutboxRunResponse> runOutbox(HttpServletRequest request) {
        ErgPrincipal principal = security.principal();
        return ok("Notification outbox processed", outboxWorker.runDueBatch(principal.tenantId()), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
