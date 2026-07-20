package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionPrototypeExposureTest {

    private static final List<String> PROTOTYPE_CONTROLLERS = List.of(
            "ai/AiContentController.java",
            "catalog/CourseController.java",
            "catalog/ElearningController.java",
            "cms/PageMenuController.java",
            "cms/PostController.java",
            "crawler/CrawlerController.java",
            "crawler/RssBlacklistController.java",
            "documents/DocumentController.java",
            "engagement/EngagementController.java",
            "operations/AnalyticsOperationsController.java",
            "platform/MigrationPlatformController.java",
            "seo/SeoController.java",
            "trending/TrendingBotController.java");

    private static final List<String> PROTOTYPE_SERVICES = List.of(
            "support/ModuleWorkflowService.java",
            "cms/CmsService.java",
            "catalog/CatalogService.java",
            "documents/DocumentService.java");

    @Test
    void processLocalPrototypeSurfaceIsNotRegisteredInProduction() throws IOException {
        for (String relative : PROTOTYPE_CONTROLLERS) {
            assertThat(source(relative))
                    .as("prototype controller %s must be profile gated", relative)
                    .contains("Profile(\"prototype\")");
        }
        for (String relative : PROTOTYPE_SERVICES) {
            assertThat(serviceSource(relative))
                    .as("prototype service %s must be profile gated", relative)
                    .contains("Profile(\"prototype\")");
        }
    }

    private String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/erg/ergspringboot/controller").resolve(relative));
    }

    private String serviceSource(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service").resolve(relative));
    }
}
