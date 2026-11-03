package com.erg.ergspringboot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The single switch for every {@code @Scheduled} worker. Tests turn it off so background jobs do not
 * write to the shared database or skew Hibernate statement counts.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "erg.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
