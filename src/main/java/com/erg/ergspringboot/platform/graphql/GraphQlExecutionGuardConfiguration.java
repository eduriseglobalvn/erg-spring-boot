package com.erg.ergspringboot.platform.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import java.util.List;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQlExecutionGuardConfiguration {

    @Bean
    GraphQlSourceBuilderCustomizer graphQlExecutionGuardCustomizer(ErgGraphQlProperties properties) {
        return builder -> builder.instrumentation(executionGuards(properties));
    }

    List<Instrumentation> executionGuards(ErgGraphQlProperties properties) {
        return List.of(
                new MaxQueryDepthInstrumentation(Math.max(1, properties.getMaxDepth())),
                new MaxQueryComplexityInstrumentation(Math.max(1, properties.getMaxComplexity())),
                new GraphQlDeadlineInstrumentation(properties.getExecutionTimeout())
        );
    }
}
