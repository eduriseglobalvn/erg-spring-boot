package com.erg.ergspringboot.platform.graphql;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GraphQlContextInterceptor implements WebGraphQlInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GraphQlContextInterceptor.class);

    private final GraphQlRequestContextFactory contextFactory;
    private final ErgGraphQlProperties properties;
    private final MeterRegistry meterRegistry;

    public GraphQlContextInterceptor(GraphQlRequestContextFactory contextFactory, ErgGraphQlProperties properties, MeterRegistry meterRegistry) {
        this.contextFactory = contextFactory;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        GraphQlRequestContext context = contextFactory.from(request.getHeaders(), request.getOperationName());
        request.configureExecutionInput((executionInput, builder) ->
                builder.graphQLContext(graphQlContext -> graphQlContext.put(GraphQlRequestContext.CONTEXT_KEY, context))
                        .build()
        );

        String operationName = context.operationName() != null ? context.operationName() : "unknown";
        Timer.Sample sample = Timer.start(meterRegistry);

        return chain.next(request).timeout(properties.getExecutionTimeout())
                .doOnSuccess(response -> {
                    sample.stop(Timer.builder("graphql.operation.duration")
                            .tag("operation", operationName)
                            .tag("result", "success")
                            .register(meterRegistry));
                })
                .doOnError(error -> {
                    sample.stop(Timer.builder("graphql.operation.duration")
                            .tag("operation", operationName)
                            .tag("result", "error")
                            .register(meterRegistry));
                    log.warn("GraphQL operation '{}' failed: {}", operationName, error.getMessage());
                });
    }
}
