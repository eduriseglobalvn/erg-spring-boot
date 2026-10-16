package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TenantMigrationPolicyContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/postgres");
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern TENANT_TABLE = Pattern.compile(
            "(?is)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z0-9_]+)\\s*\\((.*?)\\);?");

    @Test
    void migrationsAfterRlsMatrixProtectTenantTablesInTheSameForwardChange() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(this::isPostMatrixMigration).sorted().toList()) {
                String sql = Files.readString(file).toLowerCase(Locale.ROOT);
                Matcher tables = TENANT_TABLE.matcher(sql);
                while (tables.find()) {
                    String table = tables.group(1);
                    String definition = tables.group(2);
                    if (!definition.contains("tenant_id")) {
                        continue;
                    }
                    require(sql, "alter table " + table + " enable row level security", file, table, violations);
                    require(sql, "create policy " + table + "_tenant_isolation on " + table,
                            file, table, violations);
                    boolean tenantKeyed = definition.contains("primary key (tenant_id")
                            || definition.contains("unique (tenant_id")
                            || sql.matches("(?s).*index[^;]*on\\s+" + Pattern.quote(table)
                                    + "\\s*\\(\\s*tenant_id[^;]*;.*");
                    if (!tenantKeyed) {
                        violations.add(file.getFileName() + ":" + table + " lacks a tenant-leading key/index");
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void runtimeRoleMigrationFailsDeploymentForOwnerOrBypassRlsConfiguration() throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve("V81__runtime_role_rls_safety_assertion.sql"))
                .toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("rolbypassrls")
                .contains("rolsuper")
                .contains("pg_get_userbyid(c.relowner) = 'erg_app'")
                .contains("raise exception 'rls trust violation");
    }

    private boolean isPostMatrixMigration(Path path) {
        Matcher matcher = VERSION.matcher(path.getFileName().toString());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) > 75;
    }

    private void require(
            String source,
            String fragment,
            Path file,
            String table,
            List<String> violations
    ) {
        if (!source.contains(fragment)) {
            violations.add(file.getFileName() + ":" + table + " missing `" + fragment + "`");
        }
    }
}
