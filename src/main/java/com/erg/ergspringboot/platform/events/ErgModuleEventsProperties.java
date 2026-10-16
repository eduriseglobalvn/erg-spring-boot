package com.erg.ergspringboot.platform.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Typed, bounded configuration for durable Spring Modulith event delivery. */
@Component
@Validated
@ConfigurationProperties("erg.module-events")
public class ErgModuleEventsProperties {

    @Valid
    private final Recovery recovery = new Recovery();

    @Valid
    private final Io io = new Io();

    @Valid
    private final Cpu cpu = new Cpu();

    public Recovery getRecovery() {
        return recovery;
    }

    public Io getIo() {
        return io;
    }

    public Cpu getCpu() {
        return cpu;
    }

    /** Defensive validation for direct construction and non-Boot embedders. */
    public void validate() {
        recovery.validate();
        io.validate();
        cpu.validate();
    }

    public static final class Recovery {
        private boolean enabled = true;
        @NotNull
        private Duration fixedDelay = Duration.ofSeconds(30);
        @Min(1)
        @Max(1000)
        private int batchSize = 100;
        @Min(1)
        @Max(128)
        private int maxInFlight = 16;
        @NotNull
        private Duration minAge = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getFixedDelay() { return fixedDelay; }
        public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxInFlight() { return maxInFlight; }
        public void setMaxInFlight(int maxInFlight) { this.maxInFlight = maxInFlight; }
        public Duration getMinAge() { return minAge; }
        public void setMinAge(Duration minAge) { this.minAge = minAge; }

        @AssertTrue(message = "erg.module-events.recovery.fixed-delay must be positive")
        public boolean isFixedDelayValid() {
            return fixedDelay != null && !fixedDelay.isZero() && !fixedDelay.isNegative();
        }

        @AssertTrue(message = "erg.module-events.recovery.min-age must not be negative")
        public boolean isMinAgeValid() {
            return minAge != null && !minAge.isNegative();
        }

        void validate() {
            if (batchSize < 1 || batchSize > 1000 || maxInFlight < 1 || maxInFlight > 128
                    || !isFixedDelayValid() || !isMinAgeValid()) {
                throw new IllegalArgumentException("Invalid Modulith event recovery bounds");
            }
        }
    }

    public static final class Io {
        @Min(1)
        private int coreSize = 8;
        @Min(1)
        private int maxSize = 16;
        @Min(0)
        @Max(128)
        private int queueCapacity;

        public int getCoreSize() { return coreSize; }
        public void setCoreSize(int coreSize) { this.coreSize = coreSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        @AssertTrue(message = "erg.module-events.io.max-size must be >= core-size")
        public boolean isPoolBoundsValid() { return maxSize >= coreSize; }

        void validate() {
            if (coreSize < 1 || maxSize < coreSize || queueCapacity < 0 || queueCapacity > 128) {
                throw new IllegalArgumentException("Invalid module-event-io executor bounds");
            }
        }
    }

    public static final class Cpu {
        @Min(1)
        private int parallelism = 4;
        @Min(0)
        @Max(256)
        private int queueCapacity = 32;

        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        void validate() {
            if (parallelism < 1 || queueCapacity < 0 || queueCapacity > 256) {
                throw new IllegalArgumentException("Invalid module-event-cpu executor bounds");
            }
        }
    }
}
