package com.erg.ergspringboot.controller.platform.graphql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;

/**
 * The web apps call GraphQL cross-origin (lms.erg.edu.vn to api.erg.edu.vn). The browser's preflight must be
 * answered like it is for the REST endpoints; a 405 leaves every GraphQL screen empty.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("postgresFixtureAvailable")
class GraphQlCorsPreflightTest {

    static boolean postgresFixtureAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowedOriginMayPreflightGraphQl() throws Exception {
        mockMvc.perform(options("/api/v1/graphql")
                        .header("Origin", "http://localhost:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3001"));
    }

    @Test
    void unknownOriginIsStillRefused() throws Exception {
        mockMvc.perform(options("/api/v1/graphql")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
