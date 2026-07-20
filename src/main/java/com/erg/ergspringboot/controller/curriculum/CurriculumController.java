package com.erg.ergspringboot.controller.curriculum;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/curriculum")
public class CurriculumController {

    private final CurriculumService curriculumService;

    public CurriculumController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping("/tree")
    public ApiResponse<CurriculumTreeResponse> tree(
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        return ok("Curriculum tree loaded", curriculumService.listTree(status, access(request)), request);
    }

    @PostMapping("/subjects")
    public ApiResponse<CurriculumSubjectResponse> createSubject(
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum subject created", curriculumService.createSubject(body, access(request)), request);
    }

    @PatchMapping("/subjects/{id}")
    public ApiResponse<CurriculumSubjectResponse> updateSubject(
            @PathVariable String id,
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum subject updated", curriculumService.updateSubject(id, body, access(request)), request);
    }

    @PostMapping("/subjects/{id}/archive")
    public ApiResponse<CurriculumSubjectResponse> archiveSubject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum subject archived", curriculumService.archiveSubject(id, access(request)), request);
    }

    @DeleteMapping("/subjects/{id}")
    public ApiResponse<CurriculumHardDeleteResponse> hardDeleteSubject(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum subject hard deleted", curriculumService.hardDeleteSubject(id, access(request)), request);
    }

    @GetMapping("/subjects/{id}/impact")
    public ApiResponse<CurriculumImpactResponse> subjectImpact(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum subject impact loaded", curriculumService.subjectImpact(id, access(request)), request);
    }

    @PostMapping("/subjects/{subjectId}/levels")
    public ApiResponse<CurriculumLevelResponse> createLevel(
            @PathVariable String subjectId,
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum level created", curriculumService.createLevel(subjectId, body, access(request)), request);
    }

    @PatchMapping("/levels/{id}")
    public ApiResponse<CurriculumLevelResponse> updateLevel(
            @PathVariable String id,
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum level updated", curriculumService.updateLevel(id, body, access(request)), request);
    }

    @PostMapping("/levels/{id}/archive")
    public ApiResponse<CurriculumLevelResponse> archiveLevel(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum level archived", curriculumService.archiveLevel(id, access(request)), request);
    }

    @DeleteMapping("/levels/{id}")
    public ApiResponse<CurriculumHardDeleteResponse> hardDeleteLevel(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum level hard deleted", curriculumService.hardDeleteLevel(id, access(request)), request);
    }

    @GetMapping("/levels/{id}/impact")
    public ApiResponse<CurriculumImpactResponse> levelImpact(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum level impact loaded", curriculumService.levelImpact(id, access(request)), request);
    }

    @PostMapping("/levels/{levelId}/topics")
    public ApiResponse<CurriculumTopicResponse> createTopic(
            @PathVariable String levelId,
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum topic created", curriculumService.createTopic(levelId, body, access(request)), request);
    }

    @PatchMapping("/topics/{id}")
    public ApiResponse<CurriculumTopicResponse> updateTopic(
            @PathVariable String id,
            @RequestBody CurriculumNodeRequest body,
            HttpServletRequest request
    ) {
        return ok("Curriculum topic updated", curriculumService.updateTopic(id, body, access(request)), request);
    }

    @PostMapping("/topics/{id}/archive")
    public ApiResponse<CurriculumTopicResponse> archiveTopic(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum topic archived", curriculumService.archiveTopic(id, access(request)), request);
    }

    @DeleteMapping("/topics/{id}")
    public ApiResponse<CurriculumHardDeleteResponse> hardDeleteTopic(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum topic hard deleted", curriculumService.hardDeleteTopic(id, access(request)), request);
    }

    @GetMapping("/topics/{id}/impact")
    public ApiResponse<CurriculumImpactResponse> topicImpact(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        return ok("Curriculum topic impact loaded", curriculumService.topicImpact(id, access(request)), request);
    }

    private UserAccessContext access(HttpServletRequest request) {
        return UserAccessContext.from(request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
