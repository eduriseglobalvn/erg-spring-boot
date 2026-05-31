package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrmSchoolMigrationTest {

    @Test
    void crmSchoolSchemaUsesEducationUnitAsTheSingleSchoolMaster() throws IOException {
        String core = Files.readString(Path.of("src/main/resources/db/migration/postgres/V1__postgres_core_schema.sql"));
        String crm = Files.readString(Path.of("src/main/resources/db/migration/postgres/V5__school_crm_schema.sql"));

        assertThat(core)
                .contains("owner_user_id")
                .contains("created_by")
                .contains("signed_at")
                .contains("activated_at");
        assertThat(crm)
                .contains("CREATE TABLE IF NOT EXISTS crm_school_profiles")
                .contains("CREATE TABLE IF NOT EXISTS crm_school_contacts")
                .contains("CREATE TABLE IF NOT EXISTS crm_school_deals")
                .contains("CREATE TABLE IF NOT EXISTS crm_school_activities")
                .contains("CREATE TABLE IF NOT EXISTS crm_referrals")
                .contains("school_id varchar(24) NOT NULL")
                .doesNotContain("CREATE TABLE IF NOT EXISTS crm_schools");
    }

    @Test
    void uuidV7ExpansionIsAppliedInANewMigration() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/postgres/V8__expand_string_ids_for_uuid_v7.sql"));

        assertThat(migration)
                .contains("ALTER TABLE users ALTER COLUMN id TYPE varchar(36)")
                .contains("ALTER TABLE user_sessions ALTER COLUMN user_id TYPE varchar(36)")
                .contains("ALTER TABLE crm_school_profiles ALTER COLUMN school_id TYPE varchar(36)");
    }

    @Test
    void crmSchoolSeedAddsDetailedPermissionsAndOperationalRoles() throws IOException {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/postgres/V6__school_crm_permission_seed.sql"));

        assertThat(seed)
                .contains("crm.school.create")
                .contains("crm.school.read")
                .contains("crm.deal.manage")
                .contains("crm.analytics.read")
                .contains("crm.contact.manage")
                .contains("organization.school.activate")
                .contains("role_seo_staff")
                .contains("role_seo_lead")
                .contains("role_seo_manager")
                .contains("role_center_manager")
                .contains("role_school_manager")
                .contains("role_executive_director");
    }
}
