package com.erg.ergspringboot.platform.graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

final class GraphQlDeadlineInstrumentation extends SimplePerformantInstrumentation {

    private static final String TIMEOUT_MESSAGE = "GraphQL execution timeout exceeded.";

    private final long timeoutNanos;
    private final LongSupplier nanoTime;

    GraphQlDeadlineInstrumentation(Duration timeout) {
        this(timeout, System::nanoTime);
    }

    GraphQlDeadlineInstrumentation(Duration timeout, LongSupplier nanoTime) {
        this.timeoutNanos = timeout == null ? 0 : Math.max(0, timeout.toNanos());
        this.nanoTime = nanoTime;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        if (timeoutNanos == 0) {
            return DeadlineState.disabled();
        }
        return new DeadlineState(nanoTime.getAsLong() + timeoutNanos);
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(
            DataFetcher<?> dataFetcher,
            InstrumentationFieldFetchParameters parameters,
            InstrumentationState state
    ) {
        if (!(state instanceof DeadlineState deadline) || deadline.isDisabled()) {
            return dataFetcher;
        }

        return environment -> {
            assertWithinDeadline(deadline);
            Object value = dataFetcher.get(environment);
            if (value instanceof CompletionStage<?> stage) {
                return stage.thenApply(result -> {
                    assertWithinDeadline(deadline);
                    return result;
                });
            }
            assertWithinDeadline(deadline);
            return value;
        };
    }

    private void assertWithinDeadline(DeadlineState state) {
        if (nanoTime.getAsLong() > state.deadlineNanos()) {
            throw new AbortExecutionException(TIMEOUT_MESSAGE);
        }
    }

    private record DeadlineState(long deadlineNanos) implements InstrumentationState {
        private static DeadlineState disabled() {
            return new DeadlineState(Long.MAX_VALUE);
        }

        private boolean isDisabled() {
            return deadlineNanos == Long.MAX_VALUE;
        }
    }
}
