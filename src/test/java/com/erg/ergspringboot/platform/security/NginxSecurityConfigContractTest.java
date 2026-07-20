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
}
