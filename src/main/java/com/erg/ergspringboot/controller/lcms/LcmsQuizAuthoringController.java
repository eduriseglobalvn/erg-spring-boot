package com.erg.ergspringboot.controller.lcms;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.ReorderRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SaveDraftRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SlideUpsertRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.CreateQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.DeleteQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ForkResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.PublishResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.SaveDraftResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationResultResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * LCMS quiz authoring writes (spec §4.2). Create is idempotent on {@code X-Idempotency-Key}; draft
 * autosave uses an optimistic {@code docVersion} lock (409 STALE_DRAFT on mismatch). Reads stay on
 * GraphQL; only mutations live here per the transport split.
 */
@RestController
public class LcmsQuizAuthoringController {

    private final QuizAuthoringService quizAuthoringService;
    private final QuizDraftService quizDraftService;
    private final QuizPublishService quizPublishService;

    public LcmsQuizAuthoringController(
            QuizAuthoringService quizAuthoringService,
            QuizDraftService quizDraftService,
            QuizPublishService quizPublishService
    ) {
        this.quizAuthoringService = quizAuthoringService;
        this.quizDraftService = quizDraftService;
        this.quizPublishService = quizPublishService;
    }

    @PostMapping("/api/v1/lcms/quizzes")
    public ApiResponse<CreateQuizResponse> create(
            @RequestBody CreateQuizRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz created", quizAuthoringService.create(access.tenantId(), access.userId(), idempotencyKey, body), request);
    }

    @PutMapping("/api/v1/lcms/quizzes/{quizId}/draft")
    public ApiResponse<SaveDraftResponse> saveDraft(
            @PathVariable String quizId,
            @RequestBody SaveDraftRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Draft saved", quizDraftService.saveDraft(access.tenantId(), quizId, body), request);
    }

    @PatchMapping("/api/v1/lcms/quizzes/{quizId}/draft")
    public ApiResponse<SaveDraftResponse> patchDraft(
            @PathVariable String quizId,
            @RequestBody SaveDraftRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Draft saved", quizDraftService.saveDraft(access.tenantId(), quizId, body), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/slides")
    public ApiResponse<SaveDraftResponse> upsertSlide(
            @PathVariable String quizId,
            @RequestBody SlideUpsertRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Slide upserted", quizDraftService.upsertSlide(access.tenantId(), quizId, body), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/reorder")
    public ApiResponse<SaveDraftResponse> reorder(
            @PathVariable String quizId,
            @RequestBody ReorderRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Slides reordered", quizDraftService.reorder(access.tenantId(), quizId, body), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/validate")
    public ApiResponse<ValidationResultResponse> validate(
            @PathVariable String quizId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz validated", quizPublishService.validate(access.tenantId(), quizId), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/publish")
    public ApiResponse<PublishResponse> publish(
            @PathVariable String quizId,
            @RequestBody PublishRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok(
                "Quiz published",
                quizPublishService.publish(access.tenantId(), quizId, access.userId(), idempotencyKey, body),
                request
        );
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/versions/{versionId}/fork")
    public ApiResponse<ForkResponse> forkVersion(
            @PathVariable String quizId,
            @PathVariable String versionId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz version forked", quizPublishService.fork(access.tenantId(), quizId, versionId, access.userId()), request);
    }

    @DeleteMapping("/api/v1/lcms/quizzes/{quizId}")
    public ApiResponse<DeleteQuizResponse> delete(
            @PathVariable String quizId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz deleted", quizAuthoringService.delete(access.tenantId(), quizId), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
