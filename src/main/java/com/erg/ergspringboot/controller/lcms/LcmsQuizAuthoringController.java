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
import com.erg.ergspringboot.lcms.LcmsOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizCreate;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizUpdate;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizPublish;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizDelete;
import com.erg.ergspringboot.platform.security.UserAccessContext;
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

    private final LcmsOperations lcms;

    public LcmsQuizAuthoringController(LcmsOperations lcms) {
        this.lcms = lcms;
    }

    @PostMapping("/api/v1/lcms/quizzes")
    @LcmsQuizCreate
    public ApiResponse<CreateQuizResponse> create(
            @RequestBody CreateQuizRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz created", lcms.createQuiz(access.tenantId(), access.userId(), idempotencyKey, body), request);
    }

    @PutMapping("/api/v1/lcms/quizzes/{quizId}/draft")
    @LcmsQuizUpdate
    public ApiResponse<SaveDraftResponse> saveDraft(
            @PathVariable String quizId,
            @RequestBody SaveDraftRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Draft saved", lcms.saveDraft(access.tenantId(), quizId,
                idempotencyKey == null || idempotencyKey.isBlank() ? body : body.withIdempotencyKey(idempotencyKey)), request);
    }

    @PatchMapping("/api/v1/lcms/quizzes/{quizId}/draft")
    @LcmsQuizUpdate
    public ApiResponse<SaveDraftResponse> patchDraft(
            @PathVariable String quizId,
            @RequestBody SaveDraftRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Draft saved", lcms.saveDraft(access.tenantId(), quizId,
                idempotencyKey == null || idempotencyKey.isBlank() ? body : body.withIdempotencyKey(idempotencyKey)), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/slides")
    @LcmsQuizUpdate
    public ApiResponse<SaveDraftResponse> upsertSlide(
            @PathVariable String quizId,
            @RequestBody SlideUpsertRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Slide upserted", lcms.upsertSlide(access.tenantId(), quizId,
                idempotencyKey == null || idempotencyKey.isBlank() ? body : body.withIdempotencyKey(idempotencyKey)), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/reorder")
    @LcmsQuizUpdate
    public ApiResponse<SaveDraftResponse> reorder(
            @PathVariable String quizId,
            @RequestBody ReorderRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Slides reordered", lcms.reorder(access.tenantId(), quizId,
                idempotencyKey == null || idempotencyKey.isBlank() ? body : body.withIdempotencyKey(idempotencyKey)), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/validate")
    @LcmsQuizUpdate
    public ApiResponse<ValidationResultResponse> validate(
            @PathVariable String quizId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz validated", lcms.validateQuiz(access.tenantId(), quizId), request);
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/publish")
    @LcmsQuizPublish
    public ApiResponse<PublishResponse> publish(
            @PathVariable String quizId,
            @RequestBody PublishRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok(
                "Quiz published",
                lcms.publishQuiz(access.tenantId(), quizId, access.userId(), idempotencyKey, body),
                request
        );
    }

    @PostMapping("/api/v1/lcms/quizzes/{quizId}/versions/{versionId}/fork")
    @LcmsQuizCreate
    public ApiResponse<ForkResponse> forkVersion(
            @PathVariable String quizId,
            @PathVariable String versionId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz version forked", lcms.forkQuizVersion(access.tenantId(), quizId, versionId, access.userId()), request);
    }

    @DeleteMapping("/api/v1/lcms/quizzes/{quizId}")
    @LcmsQuizDelete
    public ApiResponse<DeleteQuizResponse> delete(
            @PathVariable String quizId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Quiz deleted", lcms.deleteQuiz(access.tenantId(), quizId), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
