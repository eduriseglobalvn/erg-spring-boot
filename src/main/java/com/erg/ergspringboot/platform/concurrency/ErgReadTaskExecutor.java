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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Bounded executor for blocking read fan-out; never uses the common ForkJoinPool. */
@Component
public class ErgReadTaskExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Duration shutdownTimeout;

    public ErgReadTaskExecutor(
            @Value("${erg.performance.read-executor.core-size:4}") int coreSize,
            @Value("${erg.performance.read-executor.max-size:4}") int maxSize,
            @Value("${erg.performance.read-executor.queue-capacity:32}") int queueCapacity,
            @Value("${erg.performance.read-executor.shutdown-timeout:5s}") Duration shutdownTimeout) {
        if (coreSize < 1 || maxSize < coreSize || queueCapacity < 0) {
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
        TenantContext tenantContext = TenantContext.current();
        SecurityContext securityContext = copySecurityContext();
        RequestExecutionContext.Carrier requestContext = RequestExecutionContext.capture();
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                TenantContext previousTenant = TenantContext.current();
                SecurityContext previousSecurity = SecurityContextHolder.getContext();
                try {
                    if (tenantContext == null) {
                        TenantContext.clear();
                    } else {
                        TenantContext.set(tenantContext);
                    }
                    SecurityContextHolder.setContext(securityContext);
                    try (RequestExecutionContext.Scope ignored = RequestExecutionContext.attach(requestContext)) {
                        result.complete(task.call());
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                } finally {
                    if (previousTenant == null) {
                        TenantContext.clear();
                    } else {
                        TenantContext.set(previousTenant);
                    }
                    SecurityContextHolder.clearContext();
                    SecurityContextHolder.setContext(previousSecurity);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private SecurityContext copySecurityContext() {
        SecurityContext source = SecurityContextHolder.getContext();
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        copy.setAuthentication(source.getAuthentication());
        return copy;
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
