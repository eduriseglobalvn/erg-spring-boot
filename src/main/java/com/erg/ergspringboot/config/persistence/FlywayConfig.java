package com.erg.ergspringboot.config.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@ImportRuntimeHints(FlywayRuntimeHints.class)
public class FlywayConfig {

    private static final String CLASSPATH_MIGRATION_LOCATION = "classpath:db/migration/postgres";
    private static final Path SOURCE_MIGRATION_LOCATION = Path.of("src/main/resources/db/migration/postgres");

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
    public Flyway postgresFlyway(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties,
            Environment environment
    ) {
        return Flyway.configure()
                .dataSource(migrationDataSource(properties, environment))
                .locations(resolveMigrationLocation(environment))
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flyway_schema_history")
                .load();
    }

    private DataSource migrationDataSource(DataSourceProperties properties, Environment environment) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.determineDriverClassName());
        dataSource.setUrl(properties.determineUrl());
        dataSource.setUsername(environment.getProperty("spring.flyway.user", properties.determineUsername()));
        dataSource.setPassword(environment.getProperty("spring.flyway.password", properties.determinePassword()));
        return dataSource;
    }

    /**
     * Keeps the JPA bootstrap dependency valid when migrations are deliberately disabled
     * (for example, the H2-backed unit-test profile). This bean never runs migrate(); it
     * only provides the named dependency required by PostgresJpaConfig.
     */
    @Bean(name = "postgresFlyway")
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
    public Flyway disabledPostgresFlyway(@Qualifier("postgresDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/postgres").load();
    }

    private String resolveMigrationLocation(Environment environment) {
        boolean productionProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!productionProfile && Files.isDirectory(SOURCE_MIGRATION_LOCATION)) {
            return "filesystem:" + SOURCE_MIGRATION_LOCATION
                    .toAbsolutePath()
                    .normalize()
                    .toString()
                    .replace('\\', '/');
        }
        return environment.getProperty("spring.flyway.locations", CLASSPATH_MIGRATION_LOCATION);
    }
}
