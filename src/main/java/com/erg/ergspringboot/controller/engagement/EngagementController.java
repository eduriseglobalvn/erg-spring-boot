package com.erg.ergspringboot.controller.engagement;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.service.support.ModuleWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1")
public class EngagementController {

    private final ModuleWorkflowService workflows;

    public EngagementController(ModuleWorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping({"/recruitment/jobs", "/reviews", "/reviews/admin", "/reviews/admin/all", "/hoclieu/community/topics", "/hoclieu/community/feed", "/hoclieu/community/posts"})
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        return okList("Engagement list loaded", workflows.list(module(request), type(request)), request);
    }

    @GetMapping({"/recruitment/jobs/{slug}", "/recruitment/tracking/{code}", "/reviews/stats", "/hoclieu/community/posts/{postId}/comments"})
    public ApiResponse<Map<String, Object>> detail(@PathVariable(required = false) String slug, @PathVariable(required = false) String code, @PathVariable(required = false) String postId, HttpServletRequest request) {
        String id = slug == null ? (code == null ? (postId == null ? "stats" : postId) : code) : slug;
        return ok("Engagement detail loaded", workflows.get(module(request), type(request), id), request);
    }

    @PostMapping({"/recruitment/apply", "/recruitment/jobs", "/reviews", "/reviews/batch/status", "/hoclieu/community/topics", "/hoclieu/community/posts", "/hoclieu/community/media/upload", "/hoclieu/community/posts/{postId}/comments", "/hoclieu/community/reactions", "/hoclieu/community/follows"})
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Engagement item saved", workflows.create(module(request), type(request), body), request);
    }

    @PutMapping("/recruitment/jobs/{id}")
    public ApiResponse<Map<String, Object>> updateJob(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok("Recruitment job updated", workflows.update("recruitment", "jobs", id, body), request);
    }

    @PatchMapping({"/recruitment/jobs/{id}/toggle-hot", "/recruitment/jobs/{id}/toggle-urgent", "/recruitment/jobs/{id}/status", "/recruitment/admin/candidates/{id}/status", "/reviews/{reviewId}/status", "/reviews/{reviewId}/approve", "/reviews/{reviewId}/reject", "/reviews/{reviewId}/feature"})
    public ApiResponse<Map<String, Object>> mark(@PathVariable(required = false) String id, @PathVariable(required = false) String reviewId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Engagement item updated", workflows.update(module(request), type(request), id == null ? reviewId : id, body), request);
    }

    @PostMapping({"/reviews/{reviewId}/helpful", "/reviews/{reviewId}/reply"})
    public ApiResponse<Map<String, Object>> reviewAction(@PathVariable String reviewId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Review action accepted", workflows.accepted("reviews", last(request) + ":" + reviewId, body), request);
    }

    @GetMapping("/recruitment/admin/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates(HttpServletRequest request) {
        return okList("Candidates loaded", workflows.list("recruitment", "candidates"), request);
    }

    @DeleteMapping({"/recruitment/jobs/{id}", "/hoclieu/community/reactions", "/hoclieu/community/follows"})
    public ApiResponse<Map<String, Object>> delete(@PathVariable(required = false) String id, HttpServletRequest request) {
        return ok("Engagement item deleted", workflows.delete(module(request), type(request), id == null ? "current" : id), request);
    }

    @GetMapping({"/public-disclosure", "/public-disclosure/"})
    public ApiResponse<List<Map<String, Object>>> disclosures(HttpServletRequest request) {
        return okList("Public disclosures loaded", workflows.list("public-disclosure", "documents"), request);
    }

    @GetMapping("/public-disclosure/{id}")
    public ApiResponse<Map<String, Object>> disclosure(@PathVariable String id, HttpServletRequest request) {
        return ok("Public disclosure loaded", workflows.get("public-disclosure", "documents", id), request);
    }

    @PostMapping({"/public-disclosure", "/public-disclosure/"})
    public ApiResponse<Map<String, Object>> createDisclosure(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return ok("Public disclosure saved", workflows.create("public-disclosure", "documents", body), request);
    }

    @DeleteMapping("/public-disclosure/{id}")
    public ApiResponse<Map<String, Object>> deleteDisclosure(@PathVariable String id, HttpServletRequest request) {
        return ok("Public disclosure deleted", workflows.delete("public-disclosure", "documents", id), request);
    }

    private ApiResponse<Map<String, Object>> ok(String message, Map<String, Object> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private ApiResponse<List<Map<String, Object>>> okList(String message, List<Map<String, Object>> data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String module(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/recruitment")) return "recruitment";
        if (path.contains("/reviews")) return "reviews";
        return "community";
    }

    private String type(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/jobs")) return "jobs";
        if (path.contains("/candidates")) return "candidates";
        if (path.contains("/topics")) return "topics";
        if (path.contains("/comments")) return "comments";
        if (path.contains("/media")) return "media";
        if (path.contains("/reactions")) return "reactions";
        if (path.contains("/follows")) return "follows";
        return path.contains("/reviews") ? "reviews" : "posts";
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
