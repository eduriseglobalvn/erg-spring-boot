package com.erg.ergspringboot.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.DockerClientFactory;

/**
 * Backs every {@code test}-profile {@code @SpringBootTest} context with a shared
 * Testcontainers PostgreSQL instance instead of H2. The PostgreSQL-only Flyway
 * migrations (e.g. {@code NOT VALID} foreign keys, {@code convalidated} checks) cannot
 * run on H2, so full-context integration tests must boot against real PostgreSQL.
 *
 * <p>Registered globally via {@code META-INF/spring.factories}, so no test class needs a
 * {@code @DynamicPropertySource}. If neither Docker nor an external PostgreSQL is
 * available the customizer is a no-op, leaving the default datasource untouched.
 */
public class PostgresTestContainerContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes
    ) {
        return new PostgresTestContainerContextCustomizer();
    }

    static final class PostgresTestContainerContextCustomizer implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            if (!Arrays.asList(mergedConfig.getActiveProfiles()).contains("test")) {
                return;
            }
            if (!databaseAvailable()) {
                // The fallback datasource is H2 and has no pre-created relational schema.
                // Let Hibernate create an isolated test schema; production and real
                // PostgreSQL integration runs continue to use validate + Flyway.
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testH2Schema", java.util.Map.of(
                                "spring.flyway.enabled", "false",
                                "spring.jpa.hibernate.ddl-auto", "create-drop",
                                "spring.jpa.properties.hibernate.hbm2ddl.auto", "create-drop"
                        ))
                );
                return;
            }
            ConfigurableEnvironment environment = context.getEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("testcontainersPostgres", new java.util.LinkedHashMap<>(
                            PostgreSqlTestDatabase.properties()))
            );
            // A real PostgreSQL integration run must migrate the schema before Hibernate validates it.
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "testcontainersPostgresFlyway",
                    java.util.Map.of(
                            "spring.flyway.enabled", "true",
                            "spring.jpa.hibernate.ddl-auto", "validate",
                            "spring.jpa.properties.hibernate.hbm2ddl.auto", "validate"
                    )));
        }

        private boolean databaseAvailable() {
            String externalUrl = System.getProperty("erg.test.postgres.url");
            if (externalUrl == null) {
                externalUrl = System.getenv("ERG_TEST_POSTGRES_URL");
            }
            if (externalUrl != null && !externalUrl.isBlank()) {
                return true;
            }
            try {
                return DockerClientFactory.instance().isDockerAvailable();
            } catch (RuntimeException unavailable) {
                // Testcontainers may throw while probing a host without a Docker
                // daemon. Preserve the documented H2 fallback in that environment.
                return false;
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PostgresTestContainerContextCustomizer;
        }

        @Override
        public int hashCode() {
            return PostgresTestContainerContextCustomizer.class.hashCode();
        }
    }
}
