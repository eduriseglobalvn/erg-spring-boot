package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalDockerDevelopmentContractTest {

    private static final Path DEV_ENV = Path.of("ops/docker/.env.dev.example");
    private static final Path DEV_COMPOSE = Path.of("ops/docker/compose.dev.yml");
    private static final Path DEV_NGINX = Path.of("ops/docker/nginx/dev.conf");

    @Test
    void ideDevProfileUsesThePersistentLocalDatabaseOwner() throws Exception {
        String environment = Files.readString(DEV_ENV);

        assertThat(environment)
                .contains("SPRING_PROFILES_ACTIVE=dev")
                .contains("SERVER_PORT=1606")
                .contains("SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/erg")
                .contains("SPRING_DATASOURCE_USERNAME=erg")
                .contains("SPRING_DATASOURCE_PASSWORD=erg-local-password")
                .contains("SPRING_FLYWAY_USER=erg")
                .contains("SPRING_FLYWAY_PASSWORD=erg-local-password")
                .contains("SPRING_DATA_REDIS_URL=redis://:erg-local-redis-state@127.0.0.1:6379")
                .contains("ERG_REDIS_STATE_URL=redis://:erg-local-redis-state@127.0.0.1:6379")
                .contains("ERG_REDIS_CACHE_URL=redis://:erg-local-redis-cache@127.0.0.1:6380")
                .doesNotContain("aivencloud.com");
    }

    @Test
    void devStackPublishesOnlyLoopbackDataPortsAndProxiesIdeThroughNginx() throws Exception {
        String compose = Files.readString(DEV_COMPOSE);
        String nginx = Files.readString(DEV_NGINX);

        assertThat(compose)
                .contains("127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432")
                .contains("127.0.0.1:${REDIS_STATE_HOST_PORT:-6379}:6379")
                .contains("127.0.0.1:${REDIS_CACHE_HOST_PORT:-6380}:6379")
                .contains("host.docker.internal:host-gateway")
                .contains("/nginx-health");
        assertThat(nginx)
                .contains("server host.docker.internal:1606")
                .contains("proxy_set_header X-Forwarded-Proto $scheme")
                .contains("location = /nginx-health");
    }
}
