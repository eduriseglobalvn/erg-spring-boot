package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ErgCachePropertiesTest {

    @Test
    void defaultsToBoundedSingleFlightAdmission() {
        assertThat(new ErgCacheProperties().getMaxInFlightLoads()).isEqualTo(1024);
    }

    @Test
    void rejectsUnboundedOrInvalidAdmission() {
        ErgCacheProperties properties = new ErgCacheProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxInFlightLoads(0));
    }
}
