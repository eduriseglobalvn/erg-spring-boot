package com.erg.ergspringboot.controller.seo;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeoController {

    private final ModuleWorkflowService workflows;

    public SeoController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /\nSitemap: /sitemap.xml\n";
    }

    @GetMapping(value = {"/sitemap.xml", "/sitemap_index.xml", "/sitemap-{sub}.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(@PathVariable(required = false) String sub) {
        String loc = sub == null || !sub.matches("[a-z0-9-]{1,64}")
                ? "https://erg.local/"
                : "https://erg.local/" + sub;
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>" + loc + "</loc><changefreq>daily</changefreq><priority>0.8</priority></url></urlset>";
    }

    @GetMapping("/sitemap/data")
    public ApiResponse<Map<String, Object>> sitemapData(HttpServletRequest request) {
        return ok("Sitemap data loaded", Map.of("urls", List.of("/"), "cacheTtlSeconds", 300), request);
    }

    @GetMapping({"/api/v1/seo/health", "/api/v1/seo/dashboard", "/api/v1/seo/performance", "/api/v1/seo/performance/queries", "/api/v1/seo/gsc/sync", "/api/v1/seo/gsc/top-posts", "/api/v1/seo/top-posts", "/api/v1/seo/gsc/auth/url", "/api/v1/seo/keyword-research", "/api/v1/seo/keyword-suggestions", "/api/v1/seo/keywords/suggest", "/api/v1/seo/keywords/autocomplete", "/api/v1/seo/keywords/trending", "/api/v1/seo/submission/logs"})
    public ApiResponse<Map<String, Object>> reads(HttpServletRequest request) {
        return ok("SEO data loaded", workflows.status("seo", last(request)), request);
    }

    @GetMapping({"/api/v1/seo/keywords", "/api/v1/seo/redirects", "/api/v1/seo/404-logs"})
    public ApiResponse<List<Map<String, Object>>> lists(HttpServletRequest request) {
        return ok("SEO list loaded", workflows.list("seo", last(request)), request);
    }

    @PostMapping({"/api/v1/seo/keywords", "/api/v1/seo/redirects", "/api/v1/seo/404", "/api/v1/seo/gsc/sync", "/api/v1/seo/gsc/auth/callback", "/api/v1/seo/dashboard/batch-analyze", "/api/v1/seo/analyze-draft", "/api/v1/seo/generate-alt-texts", "/api/v1/seo/suggest-meta", "/api/v1/seo/suggest-titles", "/api/v1/seo/check-duplicate", "/api/v1/seo/submission/submit"})
    public ApiResponse<Map<String, Object>> creates(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("SEO operation accepted", workflows.create("seo", last(request), body), request);
    }

    @DeleteMapping({"/api/v1/seo/keywords/{id}", "/api/v1/seo/redirects/{id}"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id, HttpServletRequest request) {
        return ok("SEO item deleted", workflows.delete("seo", last(request), id), request);
    }

    @PutMapping({"/api/v1/seo/redirects/{id}", "/api/v1/seo/config/{key}", "/api/v1/seo/apply-autolinks/{postId}"})
    public ApiResponse<Map<String, Object>> update(@PathVariable(required = false) String id, @PathVariable(required = false) String key, @PathVariable(required = false) String postId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String target = id == null ? (key == null ? postId : key) : id;
        return ok("SEO item updated", workflows.update("seo", last(request), target, body), request);
    }

    @GetMapping({"/api/v1/seo/schema/{postId}", "/api/v1/seo/gsc/{postId}", "/api/v1/seo/config/{key}", "/api/v1/seo/analyze/{postId}", "/api/v1/seo/history/{postId}", "/api/v1/seo/trends/{postId}"})
    public ApiResponse<Map<String, Object>> detail(@PathVariable(required = false) String postId, @PathVariable(required = false) String key, HttpServletRequest request) {
        return ok("SEO detail loaded", workflows.get("seo", last(request), postId == null ? key : postId), request);
    }

    @PostMapping({"/api/v1/seo/schema/{postId}", "/api/v1/seo/schema/{postId}/validate", "/api/v1/seo/posts/{postId}/robots"})
    public ApiResponse<Map<String, Object>> postOperation(@PathVariable String postId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("SEO post operation accepted", workflows.accepted("seo", last(request) + ":" + postId, body), request);
    }

    private ApiResponse<Map<String, Object>> ok(String message, Map<String, Object> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private ApiResponse<List<Map<String, Object>>> ok(String message, List<Map<String, Object>> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }

    private String last(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
