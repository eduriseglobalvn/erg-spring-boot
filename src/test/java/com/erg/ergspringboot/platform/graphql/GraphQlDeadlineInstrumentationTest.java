package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetcher;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GraphQlDeadlineInstrumentationTest {

    @Test
    void abortsBeforeDataFetcherWhenDeadlineAlreadyExpired() {
        AtomicLong now = new AtomicLong(100);
        GraphQlDeadlineInstrumentation instrumentation = new GraphQlDeadlineInstrumentation(Duration.ofNanos(10), now::get);
        InstrumentationState state = instrumentation.createState(null);
        now.set(111);

        DataFetcher<?> guarded = instrumentation.instrumentDataFetcher(
                environment -> "should-not-run",
                null,
                state
        );

        assertThatThrownBy(() -> guarded.get(null))
                .isInstanceOf(AbortExecutionException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void abortsAfterSlowDataFetcherCrossesDeadline() {
        AtomicLong now = new AtomicLong(100);
        GraphQlDeadlineInstrumentation instrumentation = new GraphQlDeadlineInstrumentation(Duration.ofNanos(10), now::get);
        InstrumentationState state = instrumentation.createState(null);
        DataFetcher<?> guarded = instrumentation.instrumentDataFetcher(
                environment -> {
                    now.set(111);
                    return "slow-result";
                },
                null,
                state
        );

        assertThatThrownBy(() -> guarded.get(null))
                .isInstanceOf(AbortExecutionException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void disabledWhenTimeoutIsZero() throws Exception {
        AtomicLong now = new AtomicLong(100);
        GraphQlDeadlineInstrumentation instrumentation = new GraphQlDeadlineInstrumentation(Duration.ZERO, now::get);
        InstrumentationState state = instrumentation.createState(null);
        DataFetcher<?> original = environment -> "ok";

        DataFetcher<?> guarded = instrumentation.instrumentDataFetcher(original, null, state);

        now.set(Long.MAX_VALUE);
        assertThat(guarded.get(null)).isEqualTo("ok");
    }
}
