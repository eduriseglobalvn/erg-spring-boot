package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoleCatalogMetadataContractTest {
    @Test
    void roleResponseExposesUsageScopeSystemAndStatusMetadata() throws Exception {
        String dto = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/common/dto/authorization/AuthorizationDtos.java"));
        assertThat(dto).contains(
                "int userCount",
                "List<String> scopeTypes",
                "boolean isSystem",
                "String status"
        );
    }
}
