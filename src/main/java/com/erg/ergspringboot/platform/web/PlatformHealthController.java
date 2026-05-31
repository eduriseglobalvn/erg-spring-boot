package com.erg.ergspringboot.platform.web;

import com.erg.ergspringboot.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformHealthController {

    private final DataSource postgresDataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    private final long startedAtMillis = System.currentTimeMillis();

    public PlatformHealthController(
            @Qualifier("postgresDataSource") DataSource postgresDataSource,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory
    ) {
        this.postgresDataSource = postgresDataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping({"/api/v1/health", "/healthz"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(HttpServletRequest request) {
        Map<String, Object> memory = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        memory.put("used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        memory.put("total", runtime.totalMemory() / 1024 / 1024);
        memory.put("unit", "MB");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("timestamp", OffsetDateTime.now().toString());
        data.put("uptime", (System.currentTimeMillis() - startedAtMillis) / 1000);
        data.put("instance", "erg-spring");
        data.put("memory", memory);
        data.put("version", "1.0.0");

        return ok(data, request);
    }

    @GetMapping({"/api/v1/ready", "/ready"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> ready(HttpServletRequest request) {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("app", true);
        checks.put("db.postgres", isDatabaseReady(postgresDataSource));
        checks.put("redis", isRedisReady());

        String status = checks.values().stream().allMatch(Boolean.TRUE::equals) ? "ready" : "degraded";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("timestamp", OffsetDateTime.now().toString());
        data.put("checks", checks);

        return ok(data, request);
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        long uptime = (System.currentTimeMillis() - startedAtMillis) / 1000;
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        return "# HELP erg_app_uptime_seconds Application uptime in seconds\n"
                + "# TYPE erg_app_uptime_seconds gauge\n"
                + "erg_app_uptime_seconds " + uptime + "\n"
                + "# HELP erg_jvm_threads_live Current live JVM thread count\n"
                + "# TYPE erg_jvm_threads_live gauge\n"
                + "erg_jvm_threads_live " + threads + "\n";
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> ok(Map<String, Object> data, HttpServletRequest request) {
        String requestId = requestId(request);
        ApiResponse<Map<String, Object>> response = ApiResponse.success(
                200,
                "Success",
                data,
                request.getRequestURI(),
                requestId
        );

        return ResponseEntity.ok()
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .body(response);
    }

    private boolean isDatabaseReady(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isRedisReady() {
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return false;
        }

        try (RedisConnection connection = factory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception exception) {
            return false;
        }
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? null : requestId.toString();
    }
}
