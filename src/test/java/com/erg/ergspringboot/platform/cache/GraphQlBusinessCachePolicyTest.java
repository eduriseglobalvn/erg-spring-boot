package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphQlBusinessCachePolicyTest {

    @Test
    void heavyLearningAndSchoolWorkspacesHaveScopedCacheBoundaries() throws Exception {
        String learning = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/learningcontent/LearningResourceLibraryReadService.java"));
        String school = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/school/SchoolOperationsReadService.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/PlatformCacheDescriptorContributor.java"));

        assertThat(learning).contains("cacheNames = \"learningResourceLibrary\"");
        assertThat(learning).contains("#tenantId", "#educationUnitId", "#userId", "#page", "#size");
        assertThat(school).contains("cacheNames = \"lcmsSchoolOperations\"");
        assertThat(school).contains("#tenantId", "#schoolId", "#selectedFields", "#canManageClasses");
        assertThat(config).contains("descriptor(\"learningResourceLibrary\"");
        assertThat(config).contains("descriptor(\"lcmsSchoolOperations\"");
    }

    @Test
    void learningResourceNativeQueryOrdersByItsQuotedProjectionAlias() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/repository/learningcontent/ContentItemRepository.java"));

        assertThat(repository).contains("cards.\"updatedAt\" desc");
        assertThat(repository).doesNotContain("cards.updated_at");
        assertThat(repository).contains("java.time.Instant getUpdatedAt()");
    }
}
