package com.erg.ergspringboot.controller.catalog;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.catalog.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CatalogService catalogService;

    public CourseController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return ok("Courses loaded", catalogService.list("courses"), request);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Course created", catalogService.create("courses", body), request);
    }

    @GetMapping("/subdomain/{subdomain}")
    public ApiResponse<Map<String, Object>> bySubdomain(@PathVariable String subdomain, HttpServletRequest request) {
        return ok("Course loaded", catalogService.findBySlug("courses", subdomain), request);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id, HttpServletRequest request) {
        return ok("Course loaded", catalogService.get("courses", id), request);
    }

    @GetMapping("/{id}/schema")
    public ApiResponse<Map<String, Object>> schema(@PathVariable String id, HttpServletRequest request) {
        return ok("Course schema loaded", catalogService.schema(id), request);
    }

    @PatchMapping({"/{id}", "/{id}/theme"})
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Course updated", catalogService.update("courses", id, body), request);
    }

    @PostMapping("/{id}/lessons/reorder")
    public ApiResponse<Map<String, Object>> reorder(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Lessons reordered", Map.of("courseId", id, "order", body), request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("Course deleted", catalogService.delete("courses", id), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
