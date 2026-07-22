package com.erg.ergspringboot.service.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthHttpLifecycleIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void invalidRefreshTokenUsesStableAuthEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-refresh-invalid")
                        .content("{\"refreshToken\":\"not-a-real-refresh-token\",\"deviceId\":\"web-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "req-refresh-invalid"))
                .andExpect(jsonPath("$.errors").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.request_id").value("req-refresh-invalid"));
    }

    @Test
    void unknownLoginUsesGenericInvalidCredentialsWithoutLeakingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-login-invalid")
                        .content("{\"identifier\":\"missing-auth-http@example.test\",\"password\":\"wrong\",\"portal\":\"lms\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "req-login-invalid"))
                .andExpect(jsonPath("$.errors").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("invalid email or password"))
                .andExpect(jsonPath("$.request_id").value("req-login-invalid"));
    }
}
