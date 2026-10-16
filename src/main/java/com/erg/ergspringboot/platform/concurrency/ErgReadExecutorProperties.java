package com.erg.ergspringboot.platform.concurrency;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded settings for blocking read fan-out.
 *
 * <p>This is deliberately a small namespace.  It keeps concurrency policy in
 * configuration metadata instead of scattering primitive {@code @Value}
 * parameters through the executor and makes the same bounds visible to JVM
 * and native-image startup.</p>
 */
@Component
@Validated
@ConfigurationProperties("erg.performance.read-executor")
public class ErgReadExecutorProperties {

    @Min(1)
    private int coreSize = 8;

    @Min(1)
    private int maxSize = 16;

    @Min(0)
    private int queueCapacity = 64;

    @NotNull
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    @AssertTrue(message = "erg.performance.read-executor.max-size must be >= core-size")
    public boolean isPoolBoundsValid() {
        return maxSize >= coreSize;
    }

    @AssertTrue(message = "erg.performance.read-executor.shutdown-timeout must be positive")
    public boolean isShutdownTimeoutValid() {
        return shutdownTimeout != null && !shutdownTimeout.isZero() && !shutdownTimeout.isNegative();
    }

    /** Defensive validation for direct construction and non-Boot embedders. */
    public void validate() {
        if (coreSize < 1 || maxSize < coreSize || queueCapacity < 0) {
            throw new IllegalArgumentException("Invalid read executor bounds");
        }
        if (!isShutdownTimeoutValid()) {
            throw new IllegalArgumentException("Read executor shutdown-timeout must be positive");
        }
    }
}
