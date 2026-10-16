package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TenantRlsMatrixIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private DataSource dataSource;

    @Test
    void everyTenantTableHasStrictReadAndWritePolicy() throws Exception {
        List<String> gaps;
        try (Connection connection = dataSource.getConnection()) {
            gaps = tenantPolicyGaps(connection);
        }

        assertThat(gaps).as("tenant tables without strict USING/WITH CHECK RLS").isEmpty();
    }

    @Test
    void catalogGateDetectsANewTenantTableWithoutPolicy() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "drop table if exists codex_unprotected_tenant_fixture");
            execute(connection, "create table codex_unprotected_tenant_fixture "
                    + "(id varchar(36) primary key, tenant_id varchar(36) not null)");
            try {
                assertThat(tenantPolicyGaps(connection))
                        .anyMatch(gap -> gap.startsWith("codex_unprotected_tenant_fixture["));
            } finally {
                execute(connection, "drop table if exists codex_unprotected_tenant_fixture");
            }
        }
    }

    private List<String> tenantPolicyGaps(Connection connection) throws Exception {
        List<String> gaps = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select c.relname,
                       c.relrowsecurity,
                       c.relforcerowsecurity,
                       coalesce(bool_or(
                           p.polqual is not null
                           and p.polwithcheck is not null
                           and pg_get_expr(p.polqual, p.polrelid) like '%app.tenant_id%'
                           and pg_get_expr(p.polwithcheck, p.polrelid) like '%app.tenant_id%'
                       ), false) as strict_policy
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                join pg_attribute a on a.attrelid = c.oid
                left join pg_policy p on p.polrelid = c.oid
                where n.nspname = 'public'
                  and c.relkind = 'r'
                  and a.attname = 'tenant_id'
                  and not a.attisdropped
                group by c.oid, c.relname, c.relrowsecurity, c.relforcerowsecurity
                having not c.relrowsecurity
                    or not coalesce(bool_or(
                        p.polqual is not null
                        and p.polwithcheck is not null
                        and pg_get_expr(p.polqual, p.polrelid) like '%app.tenant_id%'
                        and pg_get_expr(p.polwithcheck, p.polrelid) like '%app.tenant_id%'
                    ), false)
                order by c.relname
                """);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                gaps.add(rows.getString("relname")
                        + "[enabled=" + rows.getBoolean("relrowsecurity")
                        + ",forced=" + rows.getBoolean("relforcerowsecurity")
                        + ",strictPolicy=" + rows.getBoolean("strict_policy") + "]");
            }
        }
        return gaps;
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
