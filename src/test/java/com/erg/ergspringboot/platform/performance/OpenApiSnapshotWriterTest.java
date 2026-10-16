package com.erg.ergspringboot.platform.performance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Explicit opt-in helper for regenerating the published OpenAPI snapshot from runtime. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSnapshotWriterTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void writesRuntimeSnapshotForContractExport() throws Exception {
        String runtimeJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path output = Path.of("target/runtime-openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, objectMapper.readTree(runtimeJson).toPrettyString() + "\n");
    }
}
