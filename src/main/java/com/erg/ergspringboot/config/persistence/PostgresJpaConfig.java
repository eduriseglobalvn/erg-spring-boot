package com.erg.ergspringboot.config.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(PostgresHikariProperties.class)
@EnableJpaRepositories(
        basePackages = {
                "com.erg.ergspringboot.repository.auth",
                "com.erg.ergspringboot.repository.authorization",
                "com.erg.ergspringboot.repository.calendar",
                "com.erg.ergspringboot.repository.crm",
                "com.erg.ergspringboot.repository.curriculum",
                "com.erg.ergspringboot.repository.lms",
                "com.erg.ergspringboot.repository.learningcontent",
                "com.erg.ergspringboot.repository.notifications",
                "com.erg.ergspringboot.repository.organization",
                "com.erg.ergspringboot.repository.outbox",
                "com.erg.ergspringboot.repository.quiz",
                "com.erg.ergspringboot.repository.reporting",
                "com.erg.ergspringboot.repository.school",
                "com.erg.ergspringboot.repository.users"
        },
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager"
)
public class PostgresJpaConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties postgresDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public HikariDataSource postgresDataSource(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties,
            PostgresHikariProperties poolProperties
    ) {
        HikariConfig hikariConfig = hikariConfig(poolProperties);
        if (hikariConfig.getJdbcUrl() == null) {
            hikariConfig.setJdbcUrl(properties.determineUrl());
        }
        if (hikariConfig.getUsername() == null) {
            hikariConfig.setUsername(properties.determineUsername());
        }
        if (hikariConfig.getPassword() == null) {
            hikariConfig.setPassword(properties.determinePassword());
        }
        if (hikariConfig.getDriverClassName() == null) {
            hikariConfig.setDriverClassName(properties.determineDriverClassName());
        }
        return new HikariDataSource(hikariConfig);
    }

    static HikariConfig hikariConfig(PostgresHikariProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(properties.poolName());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setMinimumIdle(properties.minimumIdle());
        config.setConnectionTimeout(properties.connectionTimeout());
        config.setValidationTimeout(properties.validationTimeout());
        config.setInitializationFailTimeout(properties.initializationFailTimeout());
        config.setIdleTimeout(properties.idleTimeout());
        config.setMaxLifetime(properties.maxLifetime());
        config.setAutoCommit(properties.autoCommit());
        return config;
    }

    @Bean
    @Primary
    public PersistenceManagedTypes postgresManagedTypes(ResourceLoader resourceLoader) {
        return new PersistenceManagedTypesScanner(resourceLoader)
                .scan(
                        "com.erg.ergspringboot.model.auth",
                        "com.erg.ergspringboot.model.authorization",
                        "com.erg.ergspringboot.model.calendar",
                        "com.erg.ergspringboot.model.crm",
                        "com.erg.ergspringboot.model.curriculum",
                        "com.erg.ergspringboot.model.learningcontent",
                        "com.erg.ergspringboot.model.lms",
                        "com.erg.ergspringboot.model.notifications",
                        "com.erg.ergspringboot.model.organization",
                        "com.erg.ergspringboot.model.outbox",
                        "com.erg.ergspringboot.model.quiz",
                        "com.erg.ergspringboot.model.reporting",
                        "com.erg.ergspringboot.model.school",
                        "com.erg.ergspringboot.model.users"
                );
    }

    @Bean
    @Primary
    @DependsOn("postgresFlyway")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            @Qualifier("postgresDataSource") DataSource dataSource,
            JpaProperties jpaProperties,
            @Qualifier("postgresManagedTypes") PersistenceManagedTypes managedTypes
    ) {
        return JpaPersistenceSupport.entityManagerFactory(
                "postgres",
                dataSource,
                jpaProperties,
                managedTypes
        );
    }

    @Bean
    @Primary
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory
    ) {
        return new TenantAwareJpaTransactionManager(factory.getObject());
    }
}
