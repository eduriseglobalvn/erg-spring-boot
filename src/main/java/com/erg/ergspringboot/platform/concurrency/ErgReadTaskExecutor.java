package com.erg.ergspringboot.platform.concurrency;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.platform.performance.RequestExecutionContext;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded executor for blocking read fan-out; never uses the common ForkJoinPool. */
@Component
public class ErgReadTaskExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Duration shutdownTimeout;

    @Autowired
    public ErgReadTaskExecutor(ErgReadExecutorProperties properties) {
        this(properties.getCoreSize(), properties.getMaxSize(), properties.getQueueCapacity(),
                properties.getShutdownTimeout());
        properties.validate();
    }

    /** Compatibility constructor for focused unit tests and old embedders. */
    public ErgReadTaskExecutor(
            int coreSize, int maxSize, int queueCapacity, Duration shutdownTimeout) {
        if (coreSize < 1 || maxSize < coreSize || queueCapacity < 0
                || shutdownTimeout == null || shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("Invalid read executor bounds");
        }
        this.shutdownTimeout = shutdownTimeout;
        var queue = queueCapacity == 0
                ? new SynchronousQueue<Runnable>()
                : new ArrayBlockingQueue<Runnable>(queueCapacity);
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                30L,
                TimeUnit.SECONDS,
                queue,
                runnable -> {
                    Thread thread = new Thread(runnable, "erg-read-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        TenantContext.Carrier tenantContext = TenantContext.capture();
        RequestExecutionContext.Carrier requestContext = RequestExecutionContext.capture();
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    try (TenantContext.Scope ignoredTenant = TenantContext.install(tenantContext);
                         RequestExecutionContext.Scope ignoredRequest = RequestExecutionContext.attach(requestContext)) {
                        result.complete(task.call());
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
