package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsOperationalMigrationContractTest {
    @Test
    void migrationDefinesVersionedDirectoryAndEnrollmentInvariants() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V88__lms_operational_write_contract.sql"));
        assertThat(sql).contains("record_version");
        assertThat(sql).contains("lms_student_enrollments");
        assertThat(sql).contains("uq_lms_enrollment_tenant_idempotency");
        assertThat(sql).contains("uq_lms_enrollment_current_membership");
        assertThat(sql).contains("lms.student.manage");
        assertThat(sql).contains("lms.subject.manage");
        assertThat(sql).contains("lms.enrollment.manage");
    }
}
