package com.erg.ergspringboot.controller.platform;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MigrationPlatformController {

    private final ModuleWorkflowService workflows;

    public MigrationPlatformController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping("/migration/data-parity-matrix")
    public ApiResponse<Map<String, Object>> dataParity(HttpServletRequest request) {
        return ok("Data parity matrix loaded", workflows.checklist("data-migration", List.of(
                "postgres schema parity",
                "mongo collection parity",
                "redis key registry",
                "idempotent index scripts",
                "remote DB/Redis health and pool metrics"
        )), request);
    }

    @GetMapping("/migration/cache-key-registry")
    public ApiResponse<Map<String, Object>> cacheKeys(HttpServletRequest request) {
        return ok("Cache key registry loaded", Map.of(
                "auth", "erg:{tenant}:auth:*",
                "lmsPackage", "erg:{tenant}:lms:quiz-package:{quizId}:{hash}",
                "idempotency", "erg:{tenant}:idem:{key}",
                "queue", "erg:{tenant}:queue:{name}",
                "dlq", "erg:{tenant}:queue:{name}:dlq"
        ), request);
    }

    @GetMapping("/migration/cutover-runbook")
    public ApiResponse<Map<String, Object>> cutover(HttpServletRequest request) {
        return ok("Cutover runbook loaded", workflows.checklist("cutover", List.of(
                "shadow Java behind reverse proxy",
                "canary auth and LMS",
                "monitor DB/Redis/queue metrics",
                "switch module route flag",
                "rollback route to Go without schema deletion"
        )), request);
    }

    @GetMapping("/migration/native-image-assessment")
    public ApiResponse<Map<String, Object>> nativeAssessment(HttpServletRequest request) {
        return ok("Native image assessment loaded", Map.of(
                "defaultProductionRuntime", "JVM",
                "nativeImageDecision", "measure first",
                "risks", List.of("Hibernate reflection", "Mongo driver", "Redis client", "mail/storage SDK", "validation/OpenAPI AOT hints")
        ), request);
    }

    @PostMapping("/migration/load-test/report")
    public ApiResponse<Map<String, Object>> loadReport(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Load test report accepted", workflows.create("migration", "load-tests", body), request);
    }

    @GetMapping("/platform/runtime-wiring")
    public ApiResponse<Map<String, Object>> runtimeWiring(HttpServletRequest request) {
        return ok("Runtime wiring loaded", workflows.checklist("platform", List.of(
                "tenant context",
                "health readiness metrics",
                "firewall precheck",
                "queue worker startup",
                "graceful shutdown"
        )), request);
    }

    @GetMapping("/platform/tenancy")
    public ApiResponse<Map<String, Object>> tenancy(HttpServletRequest request) {
        return ok("Tenancy config loaded", Map.of(
                "defaultTenant", "erg",
                "header", "X-Tenant-ID",
                "redisPrefix", "erg:{tenant}:",
                "mongoStrategy", "shared database with tenant discriminator",
                "queuePrefix", "erg:{tenant}:queue:"
        ), request);
    }

    @GetMapping("/platform/shared-libraries")
    public ApiResponse<Map<String, Object>> sharedLibraries(HttpServletRequest request) {
        return ok("Shared library migration decisions loaded", workflows.checklist("shared-libraries", List.of(
                "RSS parser",
                "sitemap parser",
                "robots parser/cache",
                "simhash deduper",
                "secret fingerprint",
                "storage upload policy"
        )), request);
    }

    private ApiResponse<Map<String, Object>> ok(String message, Map<String, Object> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
