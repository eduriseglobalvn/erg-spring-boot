package com.erg.ergspringboot.controller.crawler;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("prototype")
@RequestMapping({"/api/v1/crawler", "/api/crawler"})
public class CrawlerController {

    private final ModuleWorkflowService workflows;

    public CrawlerController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping({"/crawl", "/smart-detect", "/smart-selector/analyze", "/auto-crawl", "/quick-add", "/url/run", "/sitemap/parse"})
    public ApiResponse<Map<String, Object>> job(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Crawler job queued", workflows.job("crawler", last(request), body), request);
    }

    @GetMapping({"/crawl/{jobId}", "/history/{jobId}"})
    public ApiResponse<Map<String, Object>> jobStatus(@PathVariable String jobId, HttpServletRequest request) {
        return ok("Crawler job loaded", workflows.get("crawler", "jobs", jobId), request);
    }

    @GetMapping(value = {"/crawl/{jobId}/stream", "/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String stream(@PathVariable(required = false) String jobId) {
        return "event: progress\ndata: {\"jobId\":\"" + (jobId == null ? "global" : jobId) + "\",\"status\":\"ready\"}\n\n";
    }

    @GetMapping({"/pipeline-status", "/active-pipelines", "/cron-presets", "/scheduler-status", "/ai-quota", "/quality-stats", "/dedup-stats", "/sitemap/discover", "/history", "/stats"})
    public ApiResponse<Map<String, Object>> reads(HttpServletRequest request) {
        return ok("Crawler data loaded", workflows.status("crawler", last(request)), request);
    }

    @GetMapping("/configs")
    public ApiResponse<List<Map<String, Object>>> configs(HttpServletRequest request) {
        return ok("Crawler configs loaded", workflows.list("crawler", "configs"), request);
    }

    @PostMapping({"/configs", "/configs/test-batch"})
    public ApiResponse<Map<String, Object>> createConfig(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Crawler config accepted", workflows.create("crawler", "configs", body), request);
    }

    @PatchMapping("/configs/{id}")
    public ApiResponse<Map<String, Object>> updateConfig(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Crawler config updated", workflows.update("crawler", "configs", id, body), request);
    }

    @DeleteMapping("/configs/{id}")
    public ApiResponse<Map<String, Object>> deleteConfig(@PathVariable String id, HttpServletRequest request) {
        return ok("Crawler config deleted", workflows.delete("crawler", "configs", id), request);
    }

    @GetMapping({"/rss", "/rss/peek/{id}"})
    public ApiResponse<Map<String, Object>> legacyRss(HttpServletRequest request) {
        return ok("RSS data loaded", workflows.status("rss", last(request)), request);
    }

    @PostMapping({"/rss", "/rss/preview", "/rss/create-selective", "/rss/trigger", "/rss/sync/{id}"})
    public ApiResponse<Map<String, Object>> legacyRssCreate(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("RSS operation accepted", workflows.accepted("rss", last(request), body), request);
    }

    @PatchMapping("/rss/{id}")
    public ApiResponse<Map<String, Object>> legacyRssUpdate(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("RSS feed updated", workflows.update("rss", "feeds", id, body), request);
    }

    @DeleteMapping("/rss/{id}")
    public ApiResponse<Map<String, Object>> legacyRssDelete(@PathVariable String id, HttpServletRequest request) {
        return ok("RSS feed deleted", workflows.delete("rss", "feeds", id), request);
    }

    @GetMapping("/blacklist")
    public ApiResponse<List<Map<String, Object>>> blacklist(HttpServletRequest request) {
        return ok("Blacklist loaded", workflows.list("crawler", "blacklist"), request);
    }

    @PostMapping("/blacklist")
    public ApiResponse<Map<String, Object>> createBlacklist(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Blacklist entry saved", workflows.create("crawler", "blacklist", body), request);
    }

    @PatchMapping("/blacklist/{id}")
    public ApiResponse<Map<String, Object>> updateBlacklist(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Blacklist entry updated", workflows.update("crawler", "blacklist", id, body), request);
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

    private String last(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
