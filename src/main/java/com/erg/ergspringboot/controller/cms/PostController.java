package com.erg.ergspringboot.controller.cms;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.cms.CmsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping("/api/v1/posts")
public class PostController {

    private final CmsService cmsService;

    public PostController(CmsService cmsService) {
        this.cmsService = cmsService;
    }

    @GetMapping({"", "/hidden", "/trash"})
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return ok("Posts loaded", cmsService.list("posts"), request);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id, HttpServletRequest request) {
        return ok("Post loaded", cmsService.get("posts", id), request);
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<Map<String, Object>> bySlug(@PathVariable String slug, HttpServletRequest request) {
        return ok("Post loaded", cmsService.bySlug("posts", slug), request);
    }

    @GetMapping("/preview/{id}")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String id, HttpServletRequest request) {
        return ok("Post preview loaded", cmsService.get("posts", id), request);
    }

    @PostMapping({"", "/preview"})
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Post saved", cmsService.save("posts", body), request);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Post updated", cmsService.update("posts", id, body), request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("Post deleted", cmsService.mark("posts", id, "deleted"), request);
    }

    @PutMapping("/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String id, HttpServletRequest request) {
        return ok("Post restored", cmsService.mark("posts", id, "draft"), request);
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Map<String, Object>> permanentDelete(@PathVariable String id, HttpServletRequest request) {
        return ok("Post permanently deleted", cmsService.delete("posts", id), request);
    }

    @PostMapping("/{id}/promote")
    public ApiResponse<Map<String, Object>> promote(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Post promoted", cmsService.update("posts", id, body == null ? Map.of("promoted", true) : body), request);
    }

    @PostMapping("/images/upload")
    public ApiResponse<Map<String, Object>> uploadImage(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Image uploaded", cmsService.imageUpload(body), request);
    }

    @DeleteMapping({"/images", "/images/id/{filename}"})
    public ApiResponse<Map<String, Object>> deleteImage(@PathVariable(required = false) String filename, HttpServletRequest request) {
        return ok("Image deleted", Map.of("deleted", true, "filename", filename == null ? "" : filename), request);
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories(HttpServletRequest request) {
        return ok("Categories loaded", cmsService.list("categories"), request);
    }

    @GetMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> category(@PathVariable String id, HttpServletRequest request) {
        return ok("Category loaded", cmsService.get("categories", id), request);
    }

    @PostMapping("/categories")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Category created", cmsService.save("categories", body), request);
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Category updated", cmsService.update("categories", id, body), request);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable String id, HttpServletRequest request) {
        return ok("Category deleted", cmsService.delete("categories", id), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
