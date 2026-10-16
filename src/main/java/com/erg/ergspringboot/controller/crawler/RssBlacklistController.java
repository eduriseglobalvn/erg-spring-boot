package com.erg.ergspringboot.controller.crawler;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
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
@RequestMapping("/api/v1")
public class RssBlacklistController {

    private final ModuleWorkflowService workflows;

    public RssBlacklistController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping({"/rss/feeds", "/rss/feeds/peek"})
    public ApiResponse<List<Map<String, Object>>> feeds(HttpServletRequest request) {
        return ok("RSS feeds loaded", workflows.list("rss", "feeds"), request);
    }

    @PostMapping({"/rss/feeds", "/rss/feeds/preview", "/rss/feeds/trigger", "/rss/feeds/create-selective"})
    public ApiResponse<Map<String, Object>> createFeed(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("RSS operation accepted", workflows.create("rss", "feeds", body), request);
    }

    @GetMapping("/rss/feeds/{id}")
    public ApiResponse<Map<String, Object>> feed(@PathVariable String id, HttpServletRequest request) {
        return ok("RSS feed loaded", workflows.get("rss", "feeds", id), request);
    }

    @PutMapping("/rss/feeds/{id}")
    public ApiResponse<Map<String, Object>> updateFeed(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("RSS feed updated", workflows.update("rss", "feeds", id, body), request);
    }

    @DeleteMapping("/rss/feeds/{id}")
    public ApiResponse<Map<String, Object>> deleteFeed(@PathVariable String id, HttpServletRequest request) {
        return ok("RSS feed deleted", workflows.delete("rss", "feeds", id), request);
    }

    @PostMapping("/rss/feeds/{id}/refresh")
    public ApiResponse<Map<String, Object>> refreshFeed(@PathVariable String id, HttpServletRequest request) {
        return ok("RSS feed refresh queued", workflows.job("rss", "refresh:" + id, Map.of("id", id)), request);
    }

    @GetMapping("/blacklist")
    public ApiResponse<List<Map<String, Object>>> blacklist(HttpServletRequest request) {
        return ok("Blacklist loaded", workflows.list("crawler", "blacklist"), request);
    }

    @PostMapping("/blacklist")
    public ApiResponse<Map<String, Object>> createBlacklist(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Blacklist entry saved", workflows.create("crawler", "blacklist", body), request);
    }

    @DeleteMapping("/blacklist/{id}")
    public ApiResponse<Map<String, Object>> deleteBlacklist(@PathVariable String id, HttpServletRequest request) {
        return ok("Blacklist entry deleted", workflows.delete("crawler", "blacklist", id), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
