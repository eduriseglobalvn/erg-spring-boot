package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchoolOperationalCoreMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgres/V50__school_operational_core.sql");

    @Test
    void addsTenantSafeSchoolFoundationWithoutDuplicatingSchoolMaster() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("ALTER TABLE education_units ADD COLUMN IF NOT EXISTS tenant_id")
                .contains("ALTER COLUMN tenant_id SET NOT NULL")
                .contains("uk_education_units_tenant_slug")
                .contains("CREATE TABLE IF NOT EXISTS school_operational_profiles")
                .contains("CREATE TABLE IF NOT EXISTS school_academic_years")
                .contains("CREATE TABLE IF NOT EXISTS school_grades")
                .doesNotContain("CREATE TABLE IF NOT EXISTS schools (");
    }

    @Test
    void enforcesAcademicYearAndDefaultPermissionContracts() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("end_year = start_year + 1")
                .contains("WHERE status = 'CURRENT'")
                .contains("lms.school.read")
                .contains("lms.school.create")
                .contains("lms.school.update")
                .contains("lms.school.archive")
                .contains("lms.class.manage");
    }
}
