package com.erg.ergspringboot.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PortalErrorEnvelopeIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/lms/assignments",
            "/api/v1/lcms/questions",
            "/api/v1/crm/seo/missing/schools",
            "/api/v1/curriculum/tree",
            "/api/v1/users/me"
    })
    void protectedPortalRoutesShareTheAuthenticationErrorContract(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Request-ID", "req-portal-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "req-portal-error"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.errors").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.request_id").value("req-portal-error"));
    }

    @org.junit.jupiter.api.Test
    void publicAuthValidationUsesTheSameStableEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-ID", "req-validation")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-ID", "req-validation"))
                .andExpect(jsonPath("$.statusCode").value(422))
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("identifier"))
                .andExpect(jsonPath("$.request_id").value("req-validation"));
    }
}
