package com.erg.ergspringboot.platform.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExecutorResourceContractTest {
    @Test
    void moduleEventLanesAreSharedAndBounded() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/concurrency/ModuleEventTaskExecutorConfig.java"));
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(source).contains("applicationTaskExecutor", "moduleEventIoExecutor", "moduleEventCpuExecutor", "AbortPolicy");
        assertThat(yaml)
                .contains("ERG_MODULE_EVENTS_IO_MAX_SIZE:16")
                .contains("ERG_MODULE_EVENTS_IO_QUEUE_CAPACITY:0")
                .contains("ERG_MODULE_EVENTS_CPU_QUEUE_CAPACITY:32");
    }
}
