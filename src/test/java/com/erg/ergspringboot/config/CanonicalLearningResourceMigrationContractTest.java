package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CanonicalLearningResourceMigrationContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/postgres");

    @Test
    void legacyBridgeSupportsBackfillAuditColumnUsedByContentMigration() throws IOException {
        String bridge = readMigration("V38__canonical_curriculum_backfill_bridge.sql");
        String contentBackfill = readMigration("V39__canonical_content_backfill_from_legacy_resources.sql");

        assertThat(bridge).contains("migration_version varchar(128)");
        assertThat(contentBackfill).contains("migration_version");
    }

    @Test
    void canonicalCutoverDropsLegacyLearningResourceTablesAndSeedsCanonicalRows() throws IOException {
        String dropLegacy = readMigration("V40__drop_legacy_hoclieu_tables.sql");
        String seed = readMigration("V41__canonical_learning_resource_seed.sql");
        String cleanup = readMigration("V42__canonical_content_cutover_cleanup.sql");
        String runtimeCleanup = readMigration("V45__canonical_runtime_naming_cleanup.sql");

        assertThat(dropLegacy)
                .contains("DROP TABLE IF EXISTS hoclieu_taxonomy CASCADE")
                .contains("DROP TABLE IF EXISTS hoclieu_resources CASCADE")
                .contains("DROP TABLE IF EXISTS hoclieu_assets CASCADE");
        assertThat(seed)
                .contains("INSERT INTO curriculum_subjects")
                .contains("INSERT INTO curriculum_levels")
                .contains("INSERT INTO curriculum_topics")
                .contains("INSERT INTO content_items")
                .contains("INSERT INTO topic_content_items");
        assertThat(cleanup)
                .contains("DELETE FROM permission_groups")
                .contains("DROP TABLE IF EXISTS legacy_id_mappings CASCADE");
        assertThat(runtimeCleanup)
                .contains("Content Studio")
                .contains("content-studio")
                .contains("DELETE FROM education_units");
    }

    @Test
    void runtimeSeedServicesDoNotRecreateLegacyLearningResourceNames() throws IOException {
        String accountSeedService = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/lms/LmsAccountBootstrapSeedService.java"
        ));

        assertThat(accountSeedService)
                .doesNotContain("Hoc Lieu")
                .doesNotContain("hoc-lieu")
                .doesNotContain("hoclieu");
    }

    @Test
    void canonicalLinkTablesIncludeBaseEntityAuditColumns() throws IOException {
        String auditColumns = readMigration("V46__canonical_content_link_audit_columns.sql");

        assertThat(auditColumns)
                .contains("ALTER TABLE content_item_assets")
                .contains("ALTER TABLE topic_content_items")
                .contains("updated_at TIMESTAMPTZ NOT NULL DEFAULT now()");
    }

    @Test
    void assessmentsUseTheSameCanonicalCurriculumAsLearningContent() throws IOException {
        String cutover = readMigration("V49__assessment_canonical_curriculum_cutover.sql");
        String questionBankReadModel = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsWorkspaceReadService.java"
        ));
        String quizReadModel = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/lcms/readmodel/LcmsQuizWorkspaceReadService.java"
        ));

        assertThat(cutover)
                .contains("REFERENCES curriculum_subjects(id)")
                .contains("REFERENCES curriculum_levels(id)")
                .contains("REFERENCES curriculum_topics(id)")
                .doesNotContain("REFERENCES lms_subjects(id)")
                .doesNotContain("REFERENCES lms_levels(id)")
                .doesNotContain("REFERENCES lms_topics(id)");
        assertThat(questionBankReadModel)
                .contains("repository.curriculum.CurriculumSubjectRepository")
                .contains("repository.curriculum.CurriculumLevelRepository")
                .contains("repository.curriculum.CurriculumTopicRepository");
        assertThat(quizReadModel)
                .contains("repository.curriculum.CurriculumSubjectRepository")
                .contains("repository.curriculum.CurriculumLevelRepository")
                .contains("repository.curriculum.CurriculumTopicRepository");
    }

    private String readMigration(String fileName) throws IOException {
        return Files.readString(MIGRATIONS.resolve(fileName));
    }
}
