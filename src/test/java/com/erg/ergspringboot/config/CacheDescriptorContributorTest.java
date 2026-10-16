package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CacheDescriptorContributorTest {

    @Test
    void contributorsPreserveTheExistingCacheCatalogWithoutDuplicates() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var descriptors = List.of(new LmsCacheDescriptorContributor(), new PlatformCacheDescriptorContributor())
                .stream().flatMap(contributor -> contributor.descriptors(mapper).stream()).toList();
        Set<String> names = new HashSet<>(descriptors.stream().map(descriptor -> descriptor.name()).toList());

        assertThat(descriptors).hasSize(26);
        assertThat(names).hasSize(26);
        assertThat(names).contains("lmsClassWorkspace", "quizRuntimePackage", "calendarWorkspace", "curriculumTree");
    }

    @Test
    void platformCacheConfigDoesNotImportBusinessDtoTypes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/config/CacheConfig.java"));
        assertThat(source).doesNotContain("common.response").doesNotContain("common.dto");
        assertThat(source).contains("CacheDescriptorContributor").contains("CacheDescriptorRegistry");
    }
}
