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
@RequestMapping({"/api/v1/elearning", "/api/v1/admin/elearning"})
public class ElearningController {

    private final CatalogService catalogService;

    public ElearningController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories(HttpServletRequest request) {
        return ok("Categories loaded", catalogService.list("categories"), request);
    }

    @GetMapping("/categories/{slug}")
    public ApiResponse<Map<String, Object>> category(@PathVariable String slug, HttpServletRequest request) {
        return ok("Category loaded", catalogService.findBySlug("categories", slug), request);
    }

    @GetMapping("/levels/{slug}")
    public ApiResponse<Map<String, Object>> level(@PathVariable String slug, HttpServletRequest request) {
        return ok("Level loaded", catalogService.findBySlug("levels", slug), request);
    }

    @PostMapping({"/categories", "/levels", "/units"})
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Elearning item created", catalogService.create(type(request), body), request);
    }

    @PatchMapping({"/categories/{id}", "/levels/{id}", "/units/{id}"})
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Elearning item updated", catalogService.update(type(request), id, body), request);
    }

    @DeleteMapping({"/categories/{id}", "/levels/{id}", "/units/{id}"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("Elearning item deleted", catalogService.delete(type(request), id), request);
    }

    private String type(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/levels")) return "levels";
        if (path.contains("/units")) return "units";
        return "categories";
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
