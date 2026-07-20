package com.erg.ergspringboot.platform.graphql;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "erg.graphql")
public class ErgGraphQlProperties {

    private boolean requireOperationName = true;
    private boolean introspectionEnabled = false;
    private int maxQueryLength = 20_000;
    private int maxRequestBodyBytes = 65_536;
    private int maxDepth = 8;
    private int maxComplexity = 500;
    private Duration executionTimeout = Duration.ofSeconds(5);
    private int maxAliases = 20;
    private boolean rejectSensitiveVariables = true;
    private int maxVariableArrayItems = 100;
    private RateLimit rateLimit = new RateLimit();

    public boolean isRequireOperationName() {
        return requireOperationName;
    }

    public void setRequireOperationName(boolean requireOperationName) {
        this.requireOperationName = requireOperationName;
    }

    public boolean isIntrospectionEnabled() {
        return introspectionEnabled;
    }

    public void setIntrospectionEnabled(boolean introspectionEnabled) {
        this.introspectionEnabled = introspectionEnabled;
    }

    public int getMaxQueryLength() {
        return maxQueryLength;
    }

    public void setMaxQueryLength(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxComplexity() {
        return maxComplexity;
    }

    public void setMaxComplexity(int maxComplexity) {
        this.maxComplexity = maxComplexity;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public int getMaxAliases() {
        return maxAliases;
    }

    public void setMaxAliases(int maxAliases) {
        this.maxAliases = maxAliases;
    }

    public boolean isRejectSensitiveVariables() {
        return rejectSensitiveVariables;
    }

    public void setRejectSensitiveVariables(boolean rejectSensitiveVariables) {
        this.rejectSensitiveVariables = rejectSensitiveVariables;
    }

    public int getMaxVariableArrayItems() {
        return maxVariableArrayItems;
    }

    public void setMaxVariableArrayItems(int maxVariableArrayItems) {
        this.maxVariableArrayItems = maxVariableArrayItems;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {
        private boolean enabled = true;
        private boolean redisEnabled = false;
        private boolean failClosedOnRedisError = true;
        private int requestsPerMinute = 120;
        private int burst = 30;

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }

        public boolean isFailClosedOnRedisError() {
            return failClosedOnRedisError;
        }

        public void setFailClosedOnRedisError(boolean failClosedOnRedisError) {
            this.failClosedOnRedisError = failClosedOnRedisError;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public int getBurst() {
            return burst;
        }

        public void setBurst(int burst) {
            this.burst = burst;
        }
    }
}
