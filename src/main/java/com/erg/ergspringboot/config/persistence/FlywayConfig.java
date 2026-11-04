package com.erg.ergspringboot.config.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@ImportRuntimeHints(FlywayRuntimeHints.class)
public class FlywayConfig {

    private static final String CLASSPATH_MIGRATION_LOCATION = "classpath:db/migration/postgres";
    /**
     * Demo data for developer databases. It sits outside {@code db/migration} so the default recursive
     * {@code classpath:db/migration} scan never sees it, and it is added only for the {@code dev}
     * profile (never together with {@code prod}). Use repeatable {@code R__} scripts only there, so
     * dev-only files can never take a version number the shared migrations need later.
     */
    static final String DEV_SEED_LOCATION = "classpath:db/seed/dev";
    private static final Path SOURCE_MIGRATION_LOCATION = Path.of("src/main/resources/db/migration/postgres");

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
    public Flyway postgresFlyway(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties,
            Environment environment
    ) {
        return Flyway.configure()
                .dataSource(migrationDataSource(properties, environment))
                .locations(resolveMigrationLocations(environment))
                .baselineOnMigrate(baselineOnMigrate(environment))
                .baselineVersion("0")
                .table("flyway_schema_history")
                .configuration(SESSION_LEVEL_LOCK)
                .initSql(demoPurgeInitSql(environment))
                .load();
    }

    /** Fresh-install demo purge switch (env {@code ERG_FLYWAY_DEMO_PURGE=true}), read by V125. */
    static final String DEMO_PURGE = "erg.flyway.demo-purge";

    /**
     * V125 removes the V4/V11/V21/V33/V68/V102 demo rows only when this session setting is on. It is a
     * runtime setting, not a Flyway placeholder, so the script checksum never depends on it and other
     * Flyway runners (tests, reset script) need no extra configuration. Parsed as a boolean so no
     * free text reaches SQL.
     */
    static String demoPurgeInitSql(Environment environment) {
        return environment.getProperty(DEMO_PURGE, Boolean.class, false) ? "SET erg.demo_purge = 'true'" : null;
    }

    /**
     * Flyway's default PostgreSQL lock is a transaction-level advisory lock held in an open
     * transaction, which makes {@code CREATE INDEX CONCURRENTLY} migrations (V114) wait on Flyway
     * itself forever. A session-level lock keeps migrations serialized without that self-deadlock.
     * Every Flyway runner (app, upgrade test, reset script) must use it.
     */
    public static final Map<String, String> SESSION_LEVEL_LOCK = Map.of("flyway.postgresql.transactional.lock", "false");

    /** One-time legacy onboarding switch for prod (env {@code ERG_FLYWAY_ALLOW_BASELINE_ON_MIGRATE=true}). */
    static final String PROD_BASELINE_OVERRIDE = "erg.flyway.allow-baseline-on-migrate";

    /**
     * Baselining a non-empty schema that has no {@code flyway_schema_history} (wrong database,
     * data-only restore) would replay V1..Vn, including destructive cleanup migrations, over real
     * data. Prod therefore never baselines unless an operator opts in explicitly for one run.
     */
    static boolean baselineOnMigrate(Environment environment) {
        if (!isProduction(environment)) {
            return true;
        }
        return environment.getProperty(PROD_BASELINE_OVERRIDE, Boolean.class, false);
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
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

    static String[] resolveMigrationLocations(Environment environment) {
        String migrations = resolveMigrationLocation(environment);
        boolean devSeed = environment.acceptsProfiles(Profiles.of("dev")) && !isProduction(environment);
        return devSeed ? new String[] {migrations, DEV_SEED_LOCATION} : new String[] {migrations};
    }

    private static String resolveMigrationLocation(Environment environment) {
        boolean productionProfile = isProduction(environment);
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
