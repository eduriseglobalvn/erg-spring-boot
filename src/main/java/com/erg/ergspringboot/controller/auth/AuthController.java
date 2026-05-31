package com.erg.ergspringboot.controller.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ForgotPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.GoogleLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RegisterRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResendPinRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResetPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.SocialLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.VerifyPinRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REGISTER_MESSAGE =
            "Registration successful. Please check your email for the verification PIN.";
    private static final String PIN_VERIFIED_MESSAGE = "PIN verified successfully";
    private static final String RESEND_PIN_MESSAGE = "If that email is registered, a new PIN has been sent.";
    private static final String FORGOT_PASSWORD_MESSAGE = "If that email is registered, a reset PIN has been sent.";
    private static final String RESET_PASSWORD_MESSAGE = "Password reset successful. Please login.";
    private static final String LOGOUT_MESSAGE = "Logout successful";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.register(request);
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                201,
                "Created",
                Map.of("message", REGISTER_MESSAGE),
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.status(201)
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.request_id())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse authResponse = authService.login(request, clientContext(request, servletRequest));
        ApiResponse<AuthResponse> response = ApiResponse.success(
                200,
                "Success",
                authResponse,
                servletRequest.getRequestURI(),
                requestId(servletRequest)
        );
        return ResponseEntity.ok()
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.request_id())
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        TokenResponse tokenResponse = authService.refresh(request, clientContext(request, servletRequest));
        return ok(tokenResponse, servletRequest);
    }

    @PostMapping("/verify-pin")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyPin(
            @Valid @RequestBody VerifyPinRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.verifyPin(request);
        return ok(Map.of("message", PIN_VERIFIED_MESSAGE), servletRequest);
    }

    @PostMapping("/resend-pin")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendPin(
            @Valid @RequestBody ResendPinRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.resendPin(request);
        return ok(Map.of("message", RESEND_PIN_MESSAGE), servletRequest);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.forgotPassword(request);
        return ok(Map.of("message", FORGOT_PASSWORD_MESSAGE), servletRequest);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        authService.resetPassword(request);
        return ok(Map.of("message", RESET_PASSWORD_MESSAGE), servletRequest);
    }

    @PostMapping("/google/login")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse authResponse = authService.googleLogin(request, clientContext(request, servletRequest));
        return ok(authResponse, servletRequest);
    }

    @GetMapping("/oauth/{provider}")
    public ResponseEntity<Void> oauthRedirect(
            @org.springframework.web.bind.annotation.PathVariable String provider,
            @RequestParam(required = false) String state,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri
    ) {
        String location = authService.buildOauthAuthorizationUrl(provider, state, redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<ApiResponse<AuthResponse>> oauthCallback(
            @org.springframework.web.bind.annotation.PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletRequest servletRequest
    ) {
        AuthResponse authResponse = authService.oauthCallback(provider, code, state, oauthClientContext(servletRequest));
        return ok(authResponse, servletRequest);
    }

    @PostMapping("/oauth/apple")
    public ResponseEntity<ApiResponse<AuthResponse>> appleSignIn(
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthResponse authResponse = authService.socialLogin("apple", request, clientContext(request, servletRequest));
        return ok(authResponse, servletRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest servletRequest) {
        authService.logout(userId(servletRequest), tenantId(servletRequest), sessionId(servletRequest));
        return ok(Map.of("message", LOGOUT_MESSAGE), servletRequest);
    }

    @GetMapping("/login-attempts")
    public ResponseEntity<ApiResponse<List<LoginAttemptResponse>>> loginAttempts(
            @RequestParam(required = false) String email,
            HttpServletRequest servletRequest
    ) {
        List<LoginAttemptResponse> data = authService.listLoginAttempts(userIdOrNull(servletRequest), email);
        return ok(data, servletRequest);
    }

    @GetMapping("/security/ip-status")
    public ResponseEntity<ApiResponse<IpSecurityStatusResponse>> ipSecurityStatus(
            @RequestParam(required = false) String email,
            HttpServletRequest servletRequest
    ) {
        IpSecurityStatusResponse data = authService.ipSecurityStatus(
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

    private AuthClientContext clientContext(GoogleLoginRequest request, HttpServletRequest servletRequest) {
        return new AuthClientContext(
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"),
                request.deviceId(),
                firstNonBlank(request.deviceName(), clientDeviceName(servletRequest)),
                request.deviceFingerprint()
        );
    }

    private AuthClientContext clientContext(SocialLoginRequest request, HttpServletRequest servletRequest) {
        return new AuthClientContext(
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"),
                request.deviceId(),
                firstNonBlank(request.deviceName(), clientDeviceName(servletRequest)),
                request.deviceFingerprint()
        );
    }

    private AuthClientContext oauthClientContext(HttpServletRequest servletRequest) {
        return new AuthClientContext(
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"),
                servletRequest.getParameter("device_id"),
                servletRequest.getParameter("device_name"),
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
                .header(RequestIdFilter.REQUEST_ID_HEADER, response.request_id())
                .body(response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
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
        String value = request.getHeader("X-User-ID");
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.trim();
    }

    private String userIdOrNull(HttpServletRequest request) {
        String value = request.getHeader("X-User-ID");
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String tenantId(HttpServletRequest request) {
        String value = request.getHeader("X-Tenant-ID");
        return value == null || value.isBlank() ? "erg" : value.trim();
    }

    private String sessionId(HttpServletRequest request) {
        String value = request.getHeader("X-Session-ID");
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
