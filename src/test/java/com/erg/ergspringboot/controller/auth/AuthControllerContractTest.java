package com.erg.ergspringboot.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
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
    void loginReturnsInternalAccountSession() throws Exception {
        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"teacher@erg.edu.vn","password":"secret123","deviceId":"web-1","portal":"lms"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("teacher@erg.edu.vn"))
                .andExpect(jsonPath("$.data.session.deviceId").value("web-1"));
    }

    @Test
    void refreshReturnsRotatedInternalSessionTokens() throws Exception {
        mockMvc().perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"refresh-token\",\"deviceId\":\"web-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("next-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("next-refresh-token"));
    }

    @Test
    void selfServiceAndSocialAuthRoutesAreNotMounted() throws Exception {
        MockMvc mvc = mockMvc();
        mvc.perform(post("/api/v1/auth/register")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/google/login")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/oauth/apple")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/forgot-password")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/reset-password")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/verify-pin")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auth/oauth/google")).andExpect(status().isNotFound());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AuthController(
                        new FakeAuthService(),
                        new com.erg.ergspringboot.platform.security.TrustedClientIpResolver("")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    private static final class FakeAuthService extends AuthService {
        private FakeAuthService() {
            super(null, null, null, null, null);
        }

        @Override
        public AuthResponse login(LoginRequest request, AuthClientContext context) {
            return new AuthResponse(
                    new ProfileResponse("user-1", "teacher@erg.edu.vn", "ERG Teacher", null, null,
                            "ACTIVE", "local", "erg", "user",
                            "local", List.of("lms.teacher"), true, "2026-07-11T00:00:00Z", null),
                    "access-token", "refresh-token", 900, "Bearer",
                    new SessionDeviceResponse("session-1", request.deviceId(), "Browser", request.portal(),
                            "127.0.0.1", "JUnit", true, false, null, "2026-07-11T00:00:00Z",
                            "2026-07-11T00:00:00Z", "2026-07-11T12:00:00Z"),
                    List.of("lms.dashboard.read"), List.of("lms"), "erg", "user");
        }

        @Override
        public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
            return new TokenResponse("next-access-token", "next-refresh-token", 900, "Bearer");
        }
    }
}
