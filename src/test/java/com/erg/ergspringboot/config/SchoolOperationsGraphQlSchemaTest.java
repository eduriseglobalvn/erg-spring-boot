package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchoolOperationsGraphQlSchemaTest {
    @Test
    void exposesReadOnlySchoolDirectoryAndOperationsQueries() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));
        assertThat(schema)
                .contains("schoolDirectory(input: SchoolDirectoryInput): SchoolDirectoryWorkspace!")
                .contains("schoolOperations(input: SchoolOperationsInput!): SchoolOperationsWorkspace!")
                .contains("type SchoolOperationsSummary")
                .contains("classes: [SchoolClass!]!")
                .contains("students: [SchoolStudent!]!")
                .contains("subjects: [SchoolSubject!]!")
                .doesNotContain("createSchool(input:")
                .doesNotContain("updateSchool(input:");
    }
}
