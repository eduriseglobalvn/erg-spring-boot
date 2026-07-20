package com.erg.ergspringboot.controller.notifications;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.HeartbeatDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.RegisterDeviceRequest;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.UnregisterDeviceRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeviceRegistrationResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.notifications.NotificationOperations;
import com.erg.ergspringboot.service.notifications.NotificationDeviceService;
import com.erg.ergspringboot.service.notifications.NotificationSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-devices")
public class NotificationDeviceController {

    private final NotificationOperations notifications;
    private final NotificationSecuritySupport security;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationDeviceController(NotificationOperations notifications, NotificationSecuritySupport security) {
        this.notifications = notifications;
        this.security = security;
    }

    @Deprecated
    NotificationDeviceController(NotificationDeviceService legacy, NotificationSecuritySupport security) {
        this(new NotificationOperations(null, legacy), security);
    }

    @PostMapping
    public ApiResponse<DeviceRegistrationResponse> register(
            @Valid @RequestBody RegisterDeviceRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String portal = security.verifiedPortal(principal, body.portal());
        return ok(
                "Notification device registered",
                notifications.registerDevice(principal.tenantId(), principal.userId(), portal, body),
                request
        );
    }

    @PostMapping("/unregister")
    public ApiResponse<Map<String, Boolean>> unregister(
            @RequestBody UnregisterDeviceRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String portal = security.verifiedPortal(principal, body.portal());
        notifications.unregisterDevice(principal.tenantId(), principal.userId(), portal, body);
        return ok("Notification device unregistered", Map.of("unregistered", true), request);
    }

    @PostMapping("/heartbeat")
    public ApiResponse<DeviceRegistrationResponse> heartbeat(
            @RequestBody HeartbeatDeviceRequest body,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String portal = security.verifiedPortal(principal, body.portal());
        return ok(
                "Notification device heartbeat accepted",
                notifications.heartbeat(principal.tenantId(), principal.userId(), portal, body),
                request
        );
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Map<String, Boolean>> revoke(
            @PathVariable String deviceId,
            @RequestParam(required = false) String portal,
            HttpServletRequest request
    ) {
        ErgPrincipal principal = security.principal();
        String verifiedPortal = security.verifiedPortal(principal, portal);
        notifications.revokeDevice(principal.tenantId(), principal.userId(), verifiedPortal, deviceId);
        return ok("Notification device revoked", Map.of("revoked", true), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
