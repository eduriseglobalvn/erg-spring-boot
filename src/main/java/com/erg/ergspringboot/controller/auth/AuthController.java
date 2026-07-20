package com.erg.ergspringboot.controller.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.platform.security.TrustedClientIpResolver;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.auth.AuthOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "erg.auth.local-login-enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private static final String LOGOUT_MESSAGE = "Logout successful";

    private final AuthOperations auth;
    private final TrustedClientIpResolver clientIpResolver;

    public AuthController(AuthOperations auth, TrustedClientIpResolver clientIpResolver) {
        this.auth = auth;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse authResponse = auth.login(request, clientContext(request, servletRequest));
        ApiResponse<AuthResponse> response = ApiResponse.success(
                200,
                "Success",
                authResponse,
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.ok()
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.requestId())
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        TokenResponse tokenResponse = auth.refresh(request, clientContext(request, servletRequest));
        return ok(tokenResponse, servletRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest servletRequest) {
        auth.logout(userId(servletRequest), tenantId(servletRequest), sessionId(servletRequest));
        return ok(Map.of("message", LOGOUT_MESSAGE), servletRequest);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logoutAll(HttpServletRequest servletRequest) {
        int revokedSessions = auth.logoutAll(userId(servletRequest), tenantId(servletRequest));
        return ok(Map.of("message", "All sessions revoked", "revokedSessions", revokedSessions), servletRequest);
    }

    @GetMapping("/login-attempts")
    public ResponseEntity<ApiResponse<List<LoginAttemptResponse>>> loginAttempts(
            @RequestParam(required = false) String email,
            HttpServletRequest servletRequest
    ) {
        List<LoginAttemptResponse> data = auth.loginAttempts(userIdOrNull(servletRequest), email);
        return ok(data, servletRequest);
    }

    @GetMapping("/security/ip-status")
    public ResponseEntity<ApiResponse<IpSecurityStatusResponse>> ipSecurityStatus(
            @RequestParam(required = false) String email,
            HttpServletRequest servletRequest
    ) {
        IpSecurityStatusResponse data = auth.ipSecurityStatus(
                email,
                new AuthClientContext(clientIp(servletRequest), servletRequest.getHeader("User-Agent"), null, null, null)
        );
        return ok(data, servletRequest);
    }

    private AuthClientContext clientContext(LoginRequest request, HttpServletRequest servletRequest) {
        return new AuthClientContext(
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"),
                request.deviceId(),
                firstNonBlank(request.deviceName(), clientDeviceName(servletRequest)),
                request.deviceFingerprint()
        );
    }

    private AuthClientContext clientContext(RefreshRequest request, HttpServletRequest servletRequest) {
        return new AuthClientContext(
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"),
                request.deviceId(),
                null,
                null
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, HttpServletRequest servletRequest) {
        ApiResponse<T> response = ApiResponse.success(
                200,
                "Success",
                data,
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.ok()
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.requestId())
                .body(response);
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private String clientDeviceName(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return "Unknown browser";
        }
        String normalized = ua.toLowerCase();
        String browser = normalized.contains("edg/") ? "Edge"
                : normalized.contains("chrome/") ? "Chrome"
                : normalized.contains("firefox/") ? "Firefox"
                : normalized.contains("safari/") ? "Safari"
                : "Browser";
        String device = normalized.contains("mobile") ? "Mobile" : "Desktop";
        return browser + " on " + device;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first.trim();
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? null : requestId.toString();
    }

    private String userId(HttpServletRequest request) {
        return SecurityUtils.requirePrincipal().userId();
    }

    private String userIdOrNull(HttpServletRequest request) {
        var principal = SecurityUtils.principal();
        return principal != null ? principal.userId() : null;
    }

    private String tenantId(HttpServletRequest request) {
        return SecurityUtils.requirePrincipal().tenantId();
    }

    private String sessionId(HttpServletRequest request) {
        var principal = SecurityUtils.principal();
        return principal != null ? principal.sessionId() : "";
    }
}
