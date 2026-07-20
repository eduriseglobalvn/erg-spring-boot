package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

class PostgresJpaConfigTest {

    @Test
    void hikariPropertiesAreCopiedBeforeThePoolIsCreated() throws Exception {
        Method dataSourceFactory = PostgresJpaConfig.class.getMethod(
                "postgresDataSource",
                org.springframework.boot.jdbc.autoconfigure.DataSourceProperties.class,
                PostgresHikariProperties.class
        );

        assertThat(PostgresHikariProperties.class.getAnnotation(ConfigurationProperties.class).value())
                .isEqualTo("spring.datasource.hikari");
        assertThat(dataSourceFactory.getReturnType()).isEqualTo(HikariDataSource.class);
        assertThat(dataSourceFactory.getAnnotation(ConfigurationProperties.class)).isNull();

        PostgresHikariProperties properties = new PostgresHikariProperties(
                "test-pool", 12, 3, 2_000, 1_500, 2, 300_000, 1_800_000, false
        );
        HikariConfig config = PostgresJpaConfig.hikariConfig(properties);

        assertThat(config.getPoolName()).isEqualTo("test-pool");
        assertThat(config.getMaximumPoolSize()).isEqualTo(12);
        assertThat(config.getMinimumIdle()).isEqualTo(3);
        assertThat(config.getConnectionTimeout()).isEqualTo(2_000);
        assertThat(config.getValidationTimeout()).isEqualTo(1_500);
        assertThat(config.getInitializationFailTimeout()).isEqualTo(2);
        assertThat(config.getIdleTimeout()).isEqualTo(300_000);
        assertThat(config.getMaxLifetime()).isEqualTo(1_800_000);
        assertThat(config.isAutoCommit()).isFalse();
    }

    @Test
    void exposesManagedEntityTypesForAotProcessing() throws Exception {
        Method entityManagerFactory = PostgresJpaConfig.class.getMethod(
                "postgresEntityManagerFactory",
                javax.sql.DataSource.class,
                org.springframework.boot.jpa.autoconfigure.JpaProperties.class,
                PersistenceManagedTypes.class
        );
        PersistenceManagedTypes managedTypes = new PostgresJpaConfig()
                .postgresManagedTypes(new DefaultResourceLoader());

        assertThat(entityManagerFactory).isNotNull();
        assertThat(managedTypes.getManagedClassNames())
                .contains(
                        "com.erg.ergspringboot.model.auth.AuthSession",
                        "com.erg.ergspringboot.model.reporting.ReportJob"
                )
                .doesNotContain(
                        "com.erg.ergspringboot.model.recruitment.RecruitmentCandidate",
                        "com.erg.ergspringboot.model.recruitment.RecruitmentJob"
                );
    }
}
