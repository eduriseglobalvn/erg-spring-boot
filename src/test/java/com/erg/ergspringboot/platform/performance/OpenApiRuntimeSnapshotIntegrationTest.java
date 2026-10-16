package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiRuntimeSnapshotIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "patch", "options", "head", "trace");

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void committedSnapshotContainsTheRuntimeCanonicalPathsAndMethods() throws Exception {
        String runtimeJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode runtimePaths = objectMapper.readTree(runtimeJson).path("paths");
        JsonNode committedPaths = objectMapper.readTree(Files.readString(
                Path.of("contracts/openapi/erg-openapi.json"))).path("paths");

        Set<String> requiredPaths = Set.of(
                "/api/v1/content/items",
                "/api/v1/content/items/{contentItemId}",
                "/api/v1/content/items/{contentItemId}/assets",
                "/api/v1/content/assets",
                "/api/v1/content/assets/{contentAssetId}",
                "/api/v1/content/lectures/google-slides",
                "/api/v1/curriculum/topics/{topicId}/content-items",
                "/api/v1/curriculum/topics/{topicId}/content-items/{placementId}",
                "/api/v1/curriculum/topics/{topicId}/content-items/reorder",
                "/api/v1/audit/events",
                "/api/v1/audit/export");
        assertThat(names(runtimePaths)).containsAll(requiredPaths);
        assertThat(names(committedPaths)).containsAll(requiredPaths);
        for (String path : requiredPaths) {
            assertThat(methods(committedPaths.path(path)))
                    .as("HTTP methods for %s", path)
                    .isEqualTo(methods(runtimePaths.path(path)));
        }
    }

    @Test
    void normalizedRuntimeDocumentExactlyMatchesPublishedSnapshot() throws Exception {
        JsonNode runtime = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode published = objectMapper.readTree(Files.readString(
                Path.of("contracts/openapi/erg-openapi.json")));

        ((ObjectNode) runtime).set("servers", published.path("servers").deepCopy());

        assertThat(runtime).isEqualTo(published);
    }

    private Set<String> names(JsonNode node) {
        Set<String> names = new TreeSet<>();
        names.addAll(node.propertyNames());
        return names;
    }

    private Set<String> methods(JsonNode path) {
        Set<String> methods = names(path);
        methods.retainAll(HTTP_METHODS);
        return methods;
    }
}
