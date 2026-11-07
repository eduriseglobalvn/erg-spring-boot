package com.erg.ergspringboot.timekeeping.internal;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Punch times come from the server clock; tests replace it with {@code Clock.fixed}. */
@Configuration(proxyBeanMethods = false)
class TimekeepingClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
