package com.erg.ergspringboot.platform.graphql;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Global backstop against metric-label cardinality growth from client-supplied GraphQL values. */
@Configuration(proxyBeanMethods = false)
public class GraphQlMetricsConfiguration {

    static final String OPERATION_DURATION_METER = "graphql.operation.duration";
    static final int MAX_OPERATION_TAG_VALUES = 200;

    @Bean
    MeterFilter graphQlOperationTagCardinalityLimit() {
        return MeterFilter.maximumAllowableTags(
                OPERATION_DURATION_METER, "operation", MAX_OPERATION_TAG_VALUES, MeterFilter.deny());
    }
}
