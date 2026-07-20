package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgresPerformanceDockerConfigTest {

    @Test
    void dockerPostgresLoadsStatementStatisticsAndIoTiming() throws Exception {
        String compose = Files.readString(Path.of("ops/docker/compose.yml"));
        String extension = Files.readString(Path.of("ops/docker/postgres/init/01-performance-extensions.sql"));

        assertThat(compose).contains(
                "shared_preload_libraries=pg_stat_statements",
                "pg_stat_statements.track=all",
                "track_io_timing=on",
                "./postgres/init:/docker-entrypoint-initdb.d:ro");
        assertThat(extension.toLowerCase()).contains("create extension if not exists pg_stat_statements");
    }
}
