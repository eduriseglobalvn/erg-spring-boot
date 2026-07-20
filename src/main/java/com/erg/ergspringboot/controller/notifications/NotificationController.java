package com.erg.ergspringboot.controller.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.UpdatePreferencesRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.ArchiveResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.MarkReadResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationItemResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.NotificationPreferenceResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.UnreadCountResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.notifications.NotificationSecuritySupport;
import com.erg.ergspringboot.service.notifications.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSecuritySupport security;

    public NotificationController(NotificationService notificationService, NotificationSecuritySupport security) {
        this.notificationService = notificationService;
        this.security = security;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationItemResponse>> list(
            @RequestParam(required = false) String portal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        PageResponse<NotificationItemResponse> data = notificationService.listInbox(
                principal.tenantId(),
                principal.userId(),
                verifiedPortal,
                status,
                page,
                size
        );
        return ok("Notifications loaded", data, request);
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Unread count loaded",
                notificationService.unreadCount(principal.tenantId(), principal.userId(), verifiedPortal),
                request
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationItemResponse> get(
            @PathVariable String id,
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Notification loaded",
                notificationService.detail(principal.tenantId(), principal.userId(), verifiedPortal, id),
                request
        );
    }

    @PatchMapping("/read-all")
    public ApiResponse<MarkReadResponse> readAll(
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Notifications marked read",
                notificationService.markAllRead(principal.tenantId(), principal.userId(), verifiedPortal),
                request
        );
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<MarkReadResponse> markRead(
            @PathVariable String id,
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Notification marked read",
                notificationService.markRead(principal.tenantId(), principal.userId(), verifiedPortal, id),
                request
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ArchiveResponse> archive(
            @PathVariable String id,
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Notification archived",
                notificationService.archive(principal.tenantId(), principal.userId(), verifiedPortal, id),
                request
        );
    }

    @GetMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceResponse>> preferences(
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        return ok(
                "Notification preferences loaded",
                notificationService.preferences(principal.tenantId(), principal.userId(), verifiedPortal),
                request
        );
    }

    @PutMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceResponse>> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, body.portal());
        return ok(
                "Notification preferences updated",
                notificationService.updatePreferences(principal.tenantId(), principal.userId(), verifiedPortal, body.preferences()),
                request
        );
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
