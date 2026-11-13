package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PostgresPerformanceDockerConfigTest {

    private static final String[] TUNING = {
            "shared_buffers=3GB", "effective_cache_size=7GB", "work_mem=8MB", "maintenance_work_mem=512MB",
            "random_page_cost=1.1", "effective_io_concurrency=200", "max_wal_size=2GB", "jit=off",
            "synchronous_commit=on", "statement_timeout=30s", "idle_in_transaction_session_timeout=30s",
            "log_min_duration_statement=250ms"};

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

    @Test
    void productionAndBenchmarkPostgresRunTheSameTuning() throws Exception {
        for (String file : new String[] {"ops/docker/compose.yml", "ops/docker/compose.benchmark.yml"}) {
            String compose = Files.readString(Path.of(file));
            assertThat(compose).as(file).contains(TUNING).contains("shm_size: 512m");
            assertThat(compose).as(file).doesNotContain("fsync=off", "full_page_writes=off");
        }
    }

    @Test
    void redisMaxmemoryStaysWellBelowTheContainerLimit() throws Exception {
        String compose = Files.readString(Path.of("ops/docker/compose.yml"));
        String env = Files.readString(Path.of("ops/docker/.env.production.example"));
        for (String name : new String[] {"state", "cache"}) {
            String variable = "REDIS_" + name.toUpperCase() + "_CONTAINER_MEMORY";
            long maxmemory = mebibytes(find(Files.readString(Path.of("ops/docker/redis/" + name + ".conf")),
                    "(?m)^maxmemory\\s+(\\w+)$"));
            long composeLimit = mebibytes(find(compose, variable + ":-(\\w+)\\}"));
            long envLimit = mebibytes(find(env, "(?m)^" + variable + "=(\\w+)$"));
            // The kernel must not kill Redis before maxmemory makes it refuse writes (fork/AOF headroom).
            assertThat(maxmemory * 4).as(name)
                    .isLessThanOrEqualTo(composeLimit * 3)
                    .isLessThanOrEqualTo(envLimit * 3);
        }
    }

    @Test
    void productionContainerLimitsFitTheSixteenGigabyteHost() throws Exception {
        long total = 0;
        for (String file : new String[] {"ops/docker/compose.yml", "ops/docker/compose.native.yml",
                "ops/docker/compose.monitor.yml"}) {
            Matcher limit = Pattern.compile("(?m)^\\s+mem_limit:\\s+(?:\\$\\{\\w+:-)?(\\w+)\\}?$")
                    .matcher(Files.readString(Path.of(file)));
            while (limit.find()) {
                total += mebibytes(limit.group(1));
            }
        }
        // fable/03 section 3: <= 12.5 GB of limits leaves the rest of 16 GB to the OS and page cache.
        assertThat(total).isGreaterThan(10_000).isLessThanOrEqualTo(12_800);
        assertThat(Files.readString(Path.of("ops/docker/.env.production.example")))
                .contains("BACKEND_REPLICAS=1", "BACKEND_CONTAINER_MEMORY=3g", "POSTGRES_MAX_CONNECTIONS=60");
    }

    private static String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }

    private static long mebibytes(String size) {
        String value = size.toLowerCase();
        long number = Long.parseLong(value.replaceAll("[a-z]+$", ""));
        return value.endsWith("g") || value.endsWith("gb") ? number * 1024 : number;
    }
}
