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
    void perIpLimitsFitAWholeSchoolBehindOneNatAddress() throws Exception {
        // OPS-01: a school computer lab shares one public IP; per-account/per-user limits stay the brute-force fence.
        String config = Files.readString(Path.of("ops/docker/nginx/native.conf"));
        assertThat(config).contains(
                "zone=erg_general:10m rate=300r/s;",
                "zone=erg_login:10m rate=30r/s;",
                "zone=erg_graphql:10m rate=150r/s;",
                "limit_req zone=erg_login burst=100 nodelay;",
                "limit_req zone=erg_graphql burst=300 nodelay;",
                "limit_req zone=erg_general burst=600;");
        // OPS-03: attendance selfies (2 MB) and multipart requests (3 MB) must reach the backend, not die as 413.
        assertThat(config).contains("client_max_body_size 4m;");
        String env = Files.readString(Path.of("ops/docker/.env.production.example"));
        assertThat(env).contains(
                "ERG_AUTH_LOGIN_RATE_LIMIT_SOURCE_PER_MINUTE=100",
                "ERG_AUTH_LOGIN_RATE_LIMIT_ACCOUNT_PER_MINUTE=10");
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
