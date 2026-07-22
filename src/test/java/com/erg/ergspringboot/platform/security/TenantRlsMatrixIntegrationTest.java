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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest
class TenantRlsMatrixIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private DataSource dataSource;

    @Test
    void everyTenantTableHasRowLevelSecurity() throws Exception {
        List<String> gaps = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        select c.relname, c.relrowsecurity, c.relforcerowsecurity
                        from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        join pg_attribute a on a.attrelid = c.oid
                        where n.nspname = 'public'
                          and c.relkind = 'r'
                          and a.attname = 'tenant_id'
                          and not a.attisdropped
                          and not c.relrowsecurity
                        order by c.relname
                        """);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                gaps.add(rows.getString(1) + "[enabled=" + rows.getBoolean(2)
                        + ",forced=" + rows.getBoolean(3) + "]");
            }
        }

        assertThat(gaps).as("tenant tables without RLS").isEmpty();
    }
}
