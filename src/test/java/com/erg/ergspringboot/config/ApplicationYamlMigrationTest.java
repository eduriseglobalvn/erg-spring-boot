package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApplicationYamlMigrationTest {

    @Test
    void applicationYamlUsesSinglePostgresDatabaseAndKeepsMongoConfiguration() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("url: ${SPRING_DATASOURCE_URL}")
                .contains("username: ${SPRING_DATASOURCE_USERNAME}")
                .contains("password: ${SPRING_DATASOURCE_PASSWORD}")
                .doesNotContain("LEARNING_DATASOURCE_URL")
                .doesNotContain("learning:")
                .contains("ddl-auto: validate")
                .contains("${SPRING_DATA_MONGODB_URI:${MONGODB__URI}}")
                .contains("${SPRING_DATA_MONGODB_DATABASE:${MONGODB__DATABASE}}")
                .contains("${REDIS__HOST:localhost}")
                .contains("${REDIS__PORT:6379}");
    }

    @Test
    void postgresCoreSchemaContainsAuthenticationColumnsWithoutPatchMigrations() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V1__postgres_core_schema.sql"
        ));

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS user_sessions")
                .contains("CREATE TABLE IF NOT EXISTS auth_login_attempts")
                .contains("portal varchar(255)")
                .contains("access_token_hash varchar(255)")
                .doesNotContain("ALTER TABLE");
    }

    @Test
    void learningMigrationsArePartOfTheSinglePostgresMigrationChain() throws IOException {
        String learningSchema = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V2__learning_resource_schema.sql"
        ));
        String lmsRuntimeSchema = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V3__lms_runtime_schema.sql"
        ));

        assertThat(learningSchema)
                .contains("CREATE TABLE IF NOT EXISTS academic_classes")
                .contains("CREATE TABLE IF NOT EXISTS lms_assignments")
                .doesNotContain("CREATE TABLE IF NOT EXISTS classes")
                .doesNotContain("CREATE TABLE IF NOT EXISTS assignments");
        assertThat(lmsRuntimeSchema)
                .contains("CREATE TABLE IF NOT EXISTS lms_subjects")
                .contains("CREATE TABLE IF NOT EXISTS lms_questions")
                .contains("CREATE TABLE IF NOT EXISTS lms_attempts")
                .doesNotContain("CREATE TABLE IF NOT EXISTS subjects")
                .doesNotContain("CREATE TABLE IF NOT EXISTS questions")
                .doesNotContain("CREATE TABLE IF NOT EXISTS attempts");
    }

    @Test
    void enterpriseSeedIsSeparatedFromSchemaMigrationsAndUsesStableReferenceIds() throws IOException {
        String lmsRuntimeSchema = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V3__lms_runtime_schema.sql"
        ));
        String enterpriseSeed = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V4__enterprise_reference_seed.sql"
        ));

        assertThat(lmsRuntimeSchema)
                .doesNotContain("INSERT INTO");
        assertThat(enterpriseSeed)
                .contains("INSERT INTO permission_groups")
                .contains("INSERT INTO permissions")
                .contains("INSERT INTO roles")
                .contains("INSERT INTO education_units")
                .contains("INSERT INTO academic_classes")
                .contains("sch_binh_an")
                .contains("cls_ba_2025_6a1")
                .contains("stu_ba_0001")
                .doesNotContain("-demo")
                .doesNotContain("Demo ");
    }
}
