package com.erg.ergspringboot.controller.lcms;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lcms.LcmsOperations;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portable offline quiz bundle export (spec §7). Returns a signed, self-contained JSON bundle a
 * disconnected player can verify and run. The answer key is embedded only for client-first packages;
 * server-authoritative bundles omit it (cannot be graded offline).
 */
@RestController
public class LcmsQuizBundleController {

    private final LcmsOperations lcms;

    public LcmsQuizBundleController(LcmsOperations lcms) {
        this.lcms = lcms;
    }

    @GetMapping("/api/v1/lcms/quizzes/{quizId}/bundle")
    public ApiResponse<Map<String, Object>> exportActive(
            @PathVariable String quizId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz bundle exported", lcms.exportActiveBundle(access.tenantId(), quizId), request);
    }

    @GetMapping("/api/v1/lcms/quiz-versions/{quizVersionId}/bundle")
    public ApiResponse<Map<String, Object>> exportVersion(
            @PathVariable String quizVersionId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz bundle exported", lcms.exportVersionBundle(access.tenantId(), quizVersionId), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
