package com.erg.ergspringboot.platform.concurrency;

import com.erg.ergspringboot.platform.events.ErgModuleEventsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Shared bounded lanes for Modulith listeners; no executor is created per module. */
@Configuration
public class ModuleEventTaskExecutorConfig {

    @Bean(name = {"applicationTaskExecutor", "moduleEventIoExecutor"})
    ThreadPoolTaskExecutor moduleEventIoExecutor(
            ErgModuleEventsProperties properties,
            ObjectProvider<MeterRegistry> metrics) {
        properties.validate();
        ErgModuleEventsProperties.Io configuration = properties.getIo();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(configuration.getCoreSize());
        executor.setMaxPoolSize(configuration.getMaxSize());
        executor.setQueueCapacity(configuration.getQueueCapacity());
        executor.setThreadNamePrefix("erg-module-event-io-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "moduleEventCpuExecutor")
    ThreadPoolTaskExecutor moduleEventCpuExecutor(
            ErgModuleEventsProperties properties) {
        properties.validate();
        ErgModuleEventsProperties.Cpu configuration = properties.getCpu();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(configuration.getParallelism());
        executor.setMaxPoolSize(configuration.getParallelism());
        executor.setQueueCapacity(configuration.getQueueCapacity());
        executor.setThreadNamePrefix("erg-module-event-cpu-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
