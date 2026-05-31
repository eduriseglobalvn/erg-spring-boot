package com.erg.ergspringboot.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ForgotPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.GoogleLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.SocialLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RegisterRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResendPinRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResetPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.VerifyPinRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.exception.GlobalExceptionHandler;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.service.auth.AuthService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerContractTest {

    @Test
    void registerReturnsGoCompatibleCreatedMessage() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .header("X-Request-ID", "req-register")
                        .content("""
                                {
                                  "email": "New.User@Example.com",
                                  "password": "secret123",
                                  "fullName": "New User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-ID", "req-register"))
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.message").value("Registration successful. Please check your email for the verification PIN."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.request_id").value("req-register"));
    }

    @Test
    void loginReturnsAuthResponseEnvelope() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .header("X-Request-ID", "req-login")
                        .content("""
                                {
                                  "email": "active@example.com",
                                  "password": "secret123",
                                  "deviceId": "web-1",
                                  "deviceName": "Chrome",
                                  "rememberMe": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("active@example.com"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.session.deviceId").value("web-1"))
                .andExpect(jsonPath("$.data.permissions[0]").value("dashboard.view"));
    }

    @Test
    void duplicateRegisterUsesAuthEmailExistsCode() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "exists@example.com",
                                  "password": "secret123",
                                  "fullName": "Existing User"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value("email already registered"))
                .andExpect(jsonPath("$.errors").value("EMAIL_EXISTS"));
    }

    @Test
    void pendingLocalLoginUsesEmailNotVerifiedCode() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value(403))
                .andExpect(jsonPath("$.message").value("email not verified"))
                .andExpect(jsonPath("$.errors").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void invalidRegisterRequestUsesValidationEnvelope() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .header("X-Request-ID", "req-invalid")
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short",
                                  "fullName": ""
                }
                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-ID", "req-invalid"))
                .andExpect(jsonPath("$.statusCode").value(422))
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email' && @.tag == 'Email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password' && @.tag == 'Size')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName' && @.tag == 'NotBlank')]").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.request_id").value("req-invalid"));
    }

    @Test
    void refreshReturnsRotatedTokenEnvelope() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {
                                  "refreshToken": "old-refresh",
                                  "deviceId": "web-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void pinAndPasswordRecoveryEndpointsReturnGoMessages() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/verify-pin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "code": "123456",
                                  "purpose": "email_verification"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("PIN verified successfully"));

        mockMvc.perform(post("/api/v1/auth/resend-pin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "purpose": "email_verification"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("If that email is registered, a new PIN has been sent."));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "active@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("If that email is registered, a reset PIN has been sent."));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "active@example.com",
                                  "code": "123456",
                                  "new_password": "new-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Password reset successful. Please login."));
    }

    @Test
    void googleLoginUsesCanonicalVersionedRoute() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());
        String body = """
                {
                  "email": "google@example.com",
                  "fullName": "Google User",
                  "googleSub": "google-sub-1",
                  "emailVerified": true,
                  "deviceId": "android-1",
                  "deviceName": "Android"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/google/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("google@example.com"))
                .andExpect(jsonPath("$.data.user.provider").value("google"))
                .andExpect(jsonPath("$.data.session.deviceId").value("android-1"));

    }

    @Test
    void oauthRedirectAndAppleSigninUseCanonicalVersionedRoutes() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(get("/api/v1/auth/oauth/google")
                        .param("state", "state-1")
                        .param("redirect_uri", "https://app.example.com/oauth/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://accounts.google.com/o/oauth2/v2/auth?state=state-1"));

        mockMvc.perform(get("/api/v1/auth/oauth/facebook/callback")
                        .param("code", "provider-code")
                        .param("state", "state-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.provider").value("facebook"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        mockMvc.perform(post("/api/v1/auth/oauth/apple")
                        .contentType("application/json")
                        .content("""
                                {
                                  "providerId": "apple-sub-1",
                                  "email": "apple@example.com",
                                  "fullName": "Apple User",
                                  "emailVerified": true,
                                  "deviceId": "ios-1",
                                  "deviceName": "iPhone"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("apple@example.com"))
                .andExpect(jsonPath("$.data.user.provider").value("apple"));
    }

    @Test
    void legacyAndLmsAuthRoutesAreNotMounted() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/auth/resend-pin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "purpose": "email_verification"
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/lms/auth/resend-pin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "purpose": "email_verification"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void logoutAndSecurityEndpointsReturnGoCompatibleEnvelopes() throws Exception {
        MockMvc mockMvc = mockMvc(new FakeAuthService());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg")
                        .header("X-Session-ID", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Logout successful"));

        mockMvc.perform(get("/api/v1/auth/login-attempts")
                        .param("email", "active@example.com")
                        .header("X-User-ID", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("attempt-1"))
                .andExpect(jsonPath("$.data[0].attemptedEmail").value("active@example.com"));

        mockMvc.perform(get("/api/v1/auth/security/ip-status")
                        .param("email", "active@example.com")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ip").value("10.0.0.1"))
                .andExpect(jsonPath("$.data.failedAttemptsIp").value(1))
                .andExpect(jsonPath("$.data.threshold").value(5));
    }

    private MockMvc mockMvc(AuthService authService) {
        return MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new RequestIdFilter())
                .build();
    }

    private static final class FakeAuthService extends AuthService {

        private FakeAuthService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public void register(RegisterRequest request) {
            if ("exists@example.com".equalsIgnoreCase(request.email())) {
                throw new AuthFailure.EmailExists();
            }
        }

        @Override
        public AuthResponse login(LoginRequest request, AuthClientContext context) {
            if ("pending@example.com".equalsIgnoreCase(request.email())) {
                throw new AuthFailure.EmailNotVerified();
            }
            return new AuthResponse(
                    new ProfileResponse(
                            "user-1",
                            "active@example.com",
                            "Active User",
                            "",
                            "",
                            "ACTIVE",
                            "local",
                            "erg",
                            "basic",
                            "",
                            "",
                            false,
                            "erg",
                            List.of("user"),
                            true,
                            "2026-05-20T00:00:00Z"
                    ),
                    "access-token",
                    "refresh-token",
                    900,
                    "Bearer",
                    new SessionDeviceResponse(
                            "session-1",
                            context.deviceId(),
                            context.deviceName(),
                            "lms",
                            context.ipAddress(),
                            context.userAgent(),
                            true,
                            false,
                            "",
                            "2026-05-20T00:00:00Z",
                            "2026-05-20T00:00:00Z",
                            "2026-05-21T00:00:00Z"
                    ),
                    List.of("dashboard.view"),
                    List.of("student"),
                    "erg",
                    "basic"
            );
        }

        @Override
        public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
            return new TokenResponse("new-access-token", "new-refresh-token", 900, "Bearer");
        }

        @Override
        public void verifyPin(VerifyPinRequest request) {
        }

        @Override
        public void resendPin(ResendPinRequest request) {
        }

        @Override
        public void forgotPassword(ForgotPasswordRequest request) {
        }

        @Override
        public void resetPassword(ResetPasswordRequest request) {
        }

        @Override
        public AuthResponse googleLogin(GoogleLoginRequest request, AuthClientContext context) {
            return authResponse(
                    "google@example.com",
                    "Google User",
                    "google",
                    context.deviceId(),
                    context.deviceName()
            );
        }

        @Override
        public String buildOauthAuthorizationUrl(String provider, String state, String redirectUri) {
            return "https://accounts.google.com/o/oauth2/v2/auth?state=" + state;
        }

        @Override
        public AuthResponse oauthCallback(String provider, String code, String state, AuthClientContext context) {
            return authResponse(
                    provider + "@example.com",
                    "OAuth User",
                    provider,
                    context.deviceId(),
                    context.deviceName()
            );
        }

        @Override
        public AuthResponse socialLogin(String provider, SocialLoginRequest request, AuthClientContext context) {
            return authResponse(
                    request.email(),
                    request.fullName(),
                    provider,
                    request.deviceId(),
                    request.deviceName()
            );
        }

        @Override
        public void logout(String userId, String tenantId, String sessionId) {
        }

        @Override
        public List<LoginAttemptResponse> listLoginAttempts(String userId, String email) {
            return List.of(new LoginAttemptResponse(
                    "attempt-1",
                    "erg",
                    userId,
                    "active@example.com",
                    "10.0.0.1",
                    "VN",
                    "Vietnam",
                    "AS",
                    "MockMvc",
                    "web-1",
                    "Chrome",
                    "lms",
                    "FAILED",
                    "bad_password",
                    "2026-05-20T00:00:00Z"
            ));
        }

        @Override
        public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
            return new IpSecurityStatusResponse(
                    context.ipAddress(),
                    false,
                    false,
                    null,
                    null,
                    false,
                    1,
                    1L,
                    900,
                    5
            );
        }

        private AuthResponse authResponse(
                String email,
                String fullName,
                String provider,
                String deviceId,
                String deviceName
        ) {
            return new AuthResponse(
                    new ProfileResponse(
                            "user-1",
                            email,
                            fullName,
                            "",
                            "",
                            "ACTIVE",
                            provider,
                            "erg",
                            "basic",
                            "google-sub-1",
                            provider.equals("google") ? email : "",
                            provider.equals("google"),
                            provider,
                            List.of("user"),
                            true,
                            "2026-05-20T00:00:00Z"
                    ),
                    "access-token",
                    "refresh-token",
                    900,
                    "Bearer",
                    new SessionDeviceResponse(
                            "session-1",
                            deviceId,
                            deviceName,
                            "lms",
                            "127.0.0.1",
                            "MockMvc",
                            true,
                            false,
                            "",
                            "2026-05-20T00:00:00Z",
                            "2026-05-20T00:00:00Z",
                            "2026-05-21T00:00:00Z"
                    ),
                    List.of("dashboard.view"),
                    List.of("student"),
                    "erg",
                    "basic"
            );
        }
    }
}
