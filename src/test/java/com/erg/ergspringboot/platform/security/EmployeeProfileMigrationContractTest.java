package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EmployeeProfileMigrationContractTest {

    @Test
    void employeeProfilesAreTenantScopedAndContainApprovedProfessionalFields() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V60__employee_profiles_and_security_feed.sql"));
        assertThat(sql).contains(
                "create table if not exists employee_profiles",
                "tenant_id",
                "user_id",
                "employee_code",
                "department_id",
                "job_title",
                "employment_type",
                "joined_at",
                "terminated_at",
                "experience_years",
                "experience_summary",
                "manager_id",
                "work_location",
                "emergency_contact",
                "unique (tenant_id, employee_code)"
        );
        assertThat(sql).contains(
                "alter table security_events add column if not exists platform",
                "result",
                "risk_code",
                "session_id",
                "metadata jsonb"
        );
    }
}
