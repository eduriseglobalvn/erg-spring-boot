package com.erg.ergspringboot.platform.concurrency;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Shared bounded lanes for Modulith listeners; no executor is created per module. */
@Configuration
public class ModuleEventTaskExecutorConfig {

    @Bean(name = {"applicationTaskExecutor", "moduleEventIoExecutor"})
    ThreadPoolTaskExecutor moduleEventIoExecutor(
            @Value("${erg.module-events.io.core-size:8}") int core,
            @Value("${erg.module-events.io.max-size:16}") int max,
            @Value("${erg.module-events.io.queue-capacity:0}") int queue,
            ObjectProvider<MeterRegistry> metrics) {
        if (core < 1 || max < core || queue < 0 || queue > 128) {
            throw new IllegalArgumentException("Invalid module-event-io executor bounds");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("erg-module-event-io-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "moduleEventCpuExecutor")
    ThreadPoolTaskExecutor moduleEventCpuExecutor(
            @org.springframework.beans.factory.annotation.Value("${erg.module-events.cpu.parallelism:4}") int parallelism,
            @org.springframework.beans.factory.annotation.Value("${erg.module-events.cpu.queue-capacity:32}") int queue) {
        if (parallelism < 1 || queue < 0 || queue > 256) {
            throw new IllegalArgumentException("Invalid module-event-cpu executor bounds");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("erg-module-event-cpu-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
