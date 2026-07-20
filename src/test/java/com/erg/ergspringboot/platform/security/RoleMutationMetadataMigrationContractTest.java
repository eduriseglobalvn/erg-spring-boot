package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoleMutationMetadataMigrationContractTest {

    @Test
    void roleRegistryPersistsTheLastMutationActor() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V62__role_mutation_metadata.sql"))
                .toLowerCase();

        assertThat(sql).contains("alter table roles", "updated_by");
    }
}
