package com.erg.ergspringboot.platform.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.performance.RequestExecutionContext;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ErgReadTaskExecutorTest {

    private ErgReadTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.close();
        }
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void propagatesTenantButNeverCapturesSecurityContext() throws Exception {
        executor = new ErgReadTaskExecutor(1, 1, 4, Duration.ofSeconds(1));
        TenantContext.set(TenantContext.of("tenant-a", "user-a", "lms"));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-a", "n/a", "ROLE_USER"));

        String value = executor.submit(() -> TenantContext.current().tenantId() + ":"
                + (SecurityContextHolder.getContext().getAuthentication() == null)).get(1, TimeUnit.SECONDS);

        assertThat(value).isEqualTo("tenant-a:true");
        assertThat(TenantContext.current().tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void rejectsWhenQueueIsFullInsteadOfUsingAnUnboundedPool() throws Exception {
        executor = new ErgReadTaskExecutor(1, 1, 0, Duration.ofSeconds(1));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return "held";
        });
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.submit(() -> "rejected").join())
                .hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        release.countDown();
    }

    @Test
    void contributesAsyncStatementsToTheParentRequestCounter() throws Exception {
        executor = new ErgReadTaskExecutor(1, 1, 4, Duration.ofSeconds(1));

        try (RequestExecutionContext.Scope ignored = RequestExecutionContext.open("GET /workspace")) {
            executor.submit(() -> {
                RequestExecutionContext.incrementStatementCount();
                return null;
            }).get(1, TimeUnit.SECONDS);

            assertThat(RequestExecutionContext.currentSnapshot().statementCount()).isEqualTo(1);
        }
    }
}
