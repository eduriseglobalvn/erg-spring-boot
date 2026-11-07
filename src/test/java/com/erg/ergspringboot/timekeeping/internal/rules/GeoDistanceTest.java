package com.erg.ergspringboot.timekeeping.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void samePointIsZero() {
        assertThat(GeoDistance.haversineMeters(21.0285, 105.8542, 21.0285, 105.8542)).isZero();
    }

    @Test
    void aboutOneHundredMetersNorth() {
        assertThat(GeoDistance.haversineMeters(21.0285, 105.8542, 21.0294, 105.8542)).isCloseTo(100.0, within(1.0));
    }
}
