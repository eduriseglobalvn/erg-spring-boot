package com.erg.ergspringboot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgreSqlTestDatabase {

    private static PostgreSQLContainer container;

    private PostgreSqlTestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    /**
     * Resolves the datasource properties backing integration tests. Prefers an external
     * PostgreSQL (via {@code ERG_TEST_POSTGRES_URL}) and otherwise starts a shared
     * Testcontainers PostgreSQL instance reused for the whole JVM.
     */
    public static Map<String, Object> properties() {
        String externalUrl = firstText(
                System.getProperty("erg.test.postgres.url"),
                System.getenv("ERG_TEST_POSTGRES_URL")
        );
        Map<String, Object> properties = new LinkedHashMap<>();
        if (externalUrl != null) {
            properties.put("spring.datasource.url", externalUrl);
            properties.put("spring.datasource.username", firstText(
                    System.getProperty("erg.test.postgres.username"),
                    System.getenv("ERG_TEST_POSTGRES_USERNAME"),
                    "postgres"
            ));
            properties.put("spring.datasource.password", firstText(
                    System.getProperty("erg.test.postgres.password"),
                    System.getenv("ERG_TEST_POSTGRES_PASSWORD"),
                    ""
            ));
            properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            properties.put("spring.datasource.hikari.maximum-pool-size", "4");
            return properties;
        }

        PostgreSQLContainer postgres = container();
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.datasource.driver-class-name", postgres.getDriverClassName());
        properties.put("spring.datasource.hikari.maximum-pool-size", "4");
        return properties;
    }

    private static synchronized PostgreSQLContainer container() {
        if (container == null) {
            container = new PostgreSQLContainer(postgresImage())
                    .withDatabaseName("erg_test")
                    .withUsername("erg_test")
                    .withPassword("erg_test");
            container.start();
        }
        return container;
    }

    private static DockerImageName postgresImage() {
        return DockerImageName.parse(firstText(
                System.getProperty("erg.test.postgres.image"),
                System.getenv("ERG_TEST_POSTGRES_IMAGE"),
                "postgres:17-alpine"
        ));
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
