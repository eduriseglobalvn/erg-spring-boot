package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NginxSecurityConfigContractTest {
    @Test
    void productionEdgeShedsLoginGraphqlAndConnectionAbuse() throws Exception {
        String config = Files.readString(Path.of("ops/docker/nginx/native.conf"));
        assertThat(config).contains("limit_req_zone", "limit_conn_zone", "limit_req", "limit_conn");
        assertThat(config).contains("/api/v1/auth/login", "/api/v1/graphql");
        assertThat(config).contains("Retry-After");
    }

    @Test
    void productionEdgeKeysOnTheVisitorAndHidesActuator() throws Exception {
        String config = Files.readString(Path.of("ops/docker/nginx/native.conf"));
        // Behind cloudflared every request comes from the Docker gateway; without this all visitors share one limit.
        assertThat(config).contains("real_ip_header CF-Connecting-IP;");
        assertThat(config).contains("location ^~ /actuator/ {").doesNotContain("$proxy_add_x_forwarded_for");
        // CF-Connecting-IP is trusted only because nothing but the local tunnel can reach the port.
        assertThat(Files.readString(Path.of("ops/docker/compose.native.yml"))).contains("\"127.0.0.1:${ERG_HTTP_PORT:-8080}:8080\"");
    }
}
