package com.erg.ergspringboot.platform.cache;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("erg.cache")
public class ErgCacheProperties {
    private boolean l2Enabled;
    private Set<String> l2Names = new LinkedHashSet<>();
    private Duration circuitOpenDuration = Duration.ofSeconds(5);
    private int circuitFailureThreshold = 3;
    private int maxInFlightLoads = 1024;

    public boolean isL2Enabled() {
        return l2Enabled;
    }

    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    public Set<String> getL2Names() {
        return Set.copyOf(l2Names);
    }

    public void setL2Names(Set<String> l2Names) {
        this.l2Names = l2Names == null ? new LinkedHashSet<>() : new LinkedHashSet<>(l2Names);
    }

    public Duration getCircuitOpenDuration() {
        return circuitOpenDuration;
    }

    public void setCircuitOpenDuration(Duration circuitOpenDuration) {
        this.circuitOpenDuration = circuitOpenDuration;
    }

    public int getCircuitFailureThreshold() {
        return circuitFailureThreshold;
    }

    public void setCircuitFailureThreshold(int circuitFailureThreshold) {
        this.circuitFailureThreshold = circuitFailureThreshold;
    }

    public int getMaxInFlightLoads() {
        return maxInFlightLoads;
    }

    public void setMaxInFlightLoads(int maxInFlightLoads) {
        if (maxInFlightLoads < 1) {
            throw new IllegalArgumentException("maxInFlightLoads must be positive");
        }
        this.maxInFlightLoads = maxInFlightLoads;
    }
}
