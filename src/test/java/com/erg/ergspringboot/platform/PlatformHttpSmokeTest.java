package com.erg.ergspringboot.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PlatformHttpSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthUsesGoCompatibleEnvelopeAndRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-ID", "req-health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-health"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.instance").value("erg-spring"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/health"))
                .andExpect(jsonPath("$.request_id").value("req-health"));
    }

    @Test
    void readinessReportsDependencyChecks() throws Exception {
        mockMvc.perform(get("/api/v1/ready"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.data.status", notNullValue()))
                .andExpect(jsonPath("$.data.checks.app").value(true))
                .andExpect(jsonPath("$.data.checks['db.postgres']").value(true));
    }

    @Test
    void legacyProbeAndMetricsPathsAreMounted() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));

        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks.app").value(true));

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"));
    }

    @Test
    void corsAllowsConfiguredFrontendAndExposesRequestId() throws Exception {
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-ID")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Deprecation")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Sunset")));
    }

    @Test
    void corsAllowsCrmLoginPreflightWithPortalHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://crm.erg.edu.vn:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-portal,x-request-id,x-tenant-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://crm.erg.edu.vn:3001"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-portal")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-tenant-id")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-request-id")));
    }

    @Test
    void corsAllowsLcmsLoginPreflightWithPortalHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://lcms.erg.edu.vn:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-portal,x-request-id,x-tenant-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://lcms.erg.edu.vn:3001"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-portal")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-tenant-id")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-request-id")));
    }

    @Test
    void scalarUiAndOpenApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", notNullValue()))
                .andExpect(jsonPath("$.info.title").value("ERG Spring API"));

        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")));
    }
}
