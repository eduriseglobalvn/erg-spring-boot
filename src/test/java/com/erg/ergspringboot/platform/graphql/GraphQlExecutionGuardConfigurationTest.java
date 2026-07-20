package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlExecutionGuardConfigurationTest {

    @Test
    void executionGuardsIncludeDepthComplexityAndDeadlineProtection() {
        ErgGraphQlProperties properties = new ErgGraphQlProperties();
        GraphQlExecutionGuardConfiguration configuration = new GraphQlExecutionGuardConfiguration();

        List<Instrumentation> instrumentations = configuration.executionGuards(properties);

        assertThat(instrumentations)
                .hasSize(3)
                .anyMatch(MaxQueryDepthInstrumentation.class::isInstance)
                .anyMatch(MaxQueryComplexityInstrumentation.class::isInstance)
                .anyMatch(GraphQlDeadlineInstrumentation.class::isInstance);
    }
}
