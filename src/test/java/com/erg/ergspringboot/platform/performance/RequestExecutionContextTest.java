package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RequestExecutionContextTest {

    @Test
    void keepsStatementCountsIsolatedBetweenConcurrentRequests() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<RequestExecutionContext.Snapshot> first = executor.submit(recording("GET /first", 2));
            Future<RequestExecutionContext.Snapshot> second = executor.submit(recording("GET /second", 7));

            assertThat(first.get().route()).isEqualTo("GET /first");
            assertThat(first.get().statementCount()).isEqualTo(2);
            assertThat(second.get().route()).isEqualTo("GET /second");
            assertThat(second.get().statementCount()).isEqualTo(7);
        }
    }

    private Callable<RequestExecutionContext.Snapshot> recording(String route, int statements) {
        return () -> {
            try (RequestExecutionContext.Scope ignored = RequestExecutionContext.open(route)) {
                for (int index = 0; index < statements; index++) {
                    RequestExecutionContext.incrementStatementCount();
                }
                return RequestExecutionContext.currentSnapshot();
            }
        };
    }
}
