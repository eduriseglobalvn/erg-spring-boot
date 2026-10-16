package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsAcademicRecordMigrationTest {
    @Test
    void migrationAddsNaturalKeysAndTenantRls() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V85__lms_attendance_gradebook_contract.sql"));
        assertThat(sql).contains("uq_lms_attendance_natural", "fk_lms_gradebook_score_column");
        assertThat(sql).contains("ENABLE ROW LEVEL SECURITY");
        assertThat(sql).contains("lms_attendance_records_tenant_isolation", "lms_gradebook_scores_tenant_isolation");
    }
}
