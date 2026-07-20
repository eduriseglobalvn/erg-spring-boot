package com.erg.ergspringboot.platform.redis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("erg.redis")
public class ErgRedisProperties {

    @Valid
    private final Endpoint state = new Endpoint(Duration.ofSeconds(1), Duration.ofSeconds(1), 16, 4);

    @Valid
    private final Endpoint cache = new Endpoint(Duration.ofMillis(250), Duration.ofMillis(500), 24, 4);

    public Endpoint getState() {
        return state;
    }

    public Endpoint getCache() {
        return cache;
    }

    @AssertTrue(message = "erg.redis.state.url must be configured when erg.redis.state.enabled=true")
    public boolean isStateUrlValid() {
        return !state.isEnabled() || state.hasUrl();
    }

    @AssertTrue(message = "erg.redis.cache.url must be configured when erg.redis.cache.enabled=true")
    public boolean isCacheUrlValid() {
        return !cache.isEnabled() || cache.hasUrl();
    }

    public static final class Endpoint {
        private boolean enabled;
        private String url = "";
        private Duration connectTimeout;
        private Duration commandTimeout;
        private int poolSize;
        private int minIdle;

        Endpoint(Duration connectTimeout, Duration commandTimeout, int poolSize, int minIdle) {
            this.connectTimeout = connectTimeout;
            this.commandTimeout = commandTimeout;
            this.poolSize = poolSize;
            this.minIdle = minIdle;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url == null ? "" : url.trim();
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        }

        public Duration getCommandTimeout() {
            return commandTimeout;
        }

        public void setCommandTimeout(Duration commandTimeout) {
            this.commandTimeout = requirePositive(commandTimeout, "commandTimeout");
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            if (poolSize < 1) {
                throw new IllegalArgumentException("Redis poolSize must be positive");
            }
            this.poolSize = poolSize;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            if (minIdle < 0) {
                throw new IllegalArgumentException("Redis minIdle must not be negative");
            }
            this.minIdle = minIdle;
        }

        boolean hasUrl() {
            return !url.isBlank();
        }

        private static Duration requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("Redis " + name + " must be positive");
            }
            return value;
        }
    }
}

