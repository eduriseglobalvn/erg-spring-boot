package com.erg.ergspringboot.config.persistence;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.erg.ergspringboot.repository.auth",
                "com.erg.ergspringboot.repository.authorization",
                "com.erg.ergspringboot.repository.crm",
                "com.erg.ergspringboot.repository.hoclieu",
                "com.erg.ergspringboot.repository.lms",
                "com.erg.ergspringboot.repository.organization",
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
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource postgresDataSource(
            @Qualifier("postgresDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    @DependsOn("postgresFlyway")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            @Qualifier("postgresDataSource") DataSource dataSource,
            JpaProperties jpaProperties
    ) {
        return JpaPersistenceSupport.entityManagerFactory(
                "postgres",
                dataSource,
                jpaProperties,
                "com.erg.ergspringboot.model.auth",
                "com.erg.ergspringboot.model.authorization",
                "com.erg.ergspringboot.model.crm",
                "com.erg.ergspringboot.model.hoclieu",
                "com.erg.ergspringboot.model.lms",
                "com.erg.ergspringboot.model.organization",
                "com.erg.ergspringboot.model.users"
        );
    }

    @Bean
    @Primary
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory
    ) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(factory.getObject());
        return transactionManager;
    }
}
