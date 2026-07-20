package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

class FlywayConfigTest {

    @Test
    void postgresFlywayIsNotCreatedWhenFlywayIsDisabled() throws Exception {
        Method method = FlywayConfig.class.getMethod("postgresFlyway", DataSourceProperties.class,
                org.springframework.core.env.Environment.class);

        ConditionalOnProperty condition = method.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("spring.flyway");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void enabledFlywayDoesNotDependOnTheHikariDataSource() throws Exception {
        Method method = FlywayConfig.class.getMethod(
                "postgresFlyway",
                DataSourceProperties.class,
                org.springframework.core.env.Environment.class
        );

        assertThat(method.getParameterTypes()).doesNotContain(javax.sql.DataSource.class);
    }

    @Test
    void productionUsesTheConfiguredFilesystemMigrationLocation() throws Exception {
        Method method = FlywayConfig.class.getDeclaredMethod(
                "resolveMigrationLocation",
                org.springframework.core.env.Environment.class
        );
        method.setAccessible(true);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.flyway.locations", "filesystem:/app/db/migration/postgres");
        environment.setActiveProfiles("prod");

        assertThat(method.invoke(new FlywayConfig(), environment))
                .isEqualTo("filesystem:/app/db/migration/postgres");
    }
}
