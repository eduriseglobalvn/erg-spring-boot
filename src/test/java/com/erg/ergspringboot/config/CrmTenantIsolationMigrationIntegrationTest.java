package com.erg.ergspringboot.config;

import com.erg.ergspringboot.config.persistence.FlywayConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V117/V118: CRM tenant_id expand/backfill/enforce plus the dedicated handover approval permission. */
@Testcontainers(disabledWithoutDocker = true)
class CrmTenantIsolationMigrationIntegrationTest {

    private static final List<String> CRM_TABLES = List.of(
            "crm_school_profiles", "crm_school_contacts", "crm_school_deals", "crm_school_activities", "crm_referrals");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_crm_tenant")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @Test
    void existingCrmRowsAreBackfilledAndTablesAreTenantProtected() throws Exception {
        flyway(MigrationVersion.fromVersion("110")).migrate();
        String schoolId;
        String tenantId;
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet unit = statement.executeQuery("select id, tenant_id from education_units order by id limit 1")) {
                assertThat(unit.next()).isTrue();
                schoolId = unit.getString(1);
                tenantId = unit.getString(2);
            }
            String userId;
            try (ResultSet user = statement.executeQuery("select id from users order by id limit 1")) {
                assertThat(user.next()).isTrue();
                userId = user.getString(1);
            }
            statement.execute("insert into crm_school_deals (id, created_at, updated_at, school_id, owner_user_id, stage) "
                    + "values ('legacy-deal-1', now(), now(), '" + schoolId + "', '" + userId + "', 'signed')");
        }

        flyway(null).migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet deal = statement.executeQuery("select tenant_id from crm_school_deals where id = 'legacy-deal-1'")) {
                assertThat(deal.next()).isTrue();
                assertThat(deal.getString(1)).isEqualTo(tenantId);
            }
            for (String table : CRM_TABLES) {
                assertThat(single(statement, "select is_nullable from information_schema.columns where table_schema = 'public' "
                        + "and table_name = '" + table + "' and column_name = 'tenant_id'")).as(table).isEqualTo("NO");
                assertThat(single(statement, "select relrowsecurity::text from pg_class where relname = '" + table
                        + "' and relkind = 'r'")).as(table).isEqualTo("true");
                assertThat(single(statement, "select count(*)::text from pg_policies where tablename = '" + table
                        + "' and qual like '%app.tenant_id%' and with_check like '%app.tenant_id%'")).as(table).isEqualTo("1");
            }
            List<String> approvers = new ArrayList<>();
            try (ResultSet roles = statement.executeQuery("""
                    select r.name from roles r
                    join role_permissions rp on rp.role_id = r.id
                    join permissions p on p.id = rp.permission_id
                    where p.name = 'crm.handover.approve'
                    """)) {
                while (roles.next()) approvers.add(roles.getString(1));
            }
            assertThat(approvers).contains("seo.manager", "seo.lead").doesNotContain("seo.staff");
        }
    }

    private String single(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).as(sql).isTrue();
            return result.getString(1);
        }
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
