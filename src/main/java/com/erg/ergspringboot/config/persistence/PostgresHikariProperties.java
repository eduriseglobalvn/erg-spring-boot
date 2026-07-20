package com.erg.ergspringboot.config.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.datasource.hikari")
public record PostgresHikariProperties(
        String poolName,
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeout,
        long validationTimeout,
        long initializationFailTimeout,
        long idleTimeout,
        long maxLifetime,
        boolean autoCommit
) {
}
