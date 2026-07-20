package com.erg.ergspringboot.controller.lcms;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LcmsQuestionBankAuthoringController {

    private final QuestionBankAuthoringService questionBankAuthoringService;

    public LcmsQuestionBankAuthoringController(QuestionBankAuthoringService questionBankAuthoringService) {
        this.questionBankAuthoringService = questionBankAuthoringService;
    }

    @PostMapping("/api/v1/lcms/questions")
    public ApiResponse<SaveQuestionResponse> create(
            @RequestBody SaveQuestionRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok(
                "Question created",
                questionBankAuthoringService.create(access.tenantId(), access.userId(), body),
                request
        );
    }

    @PutMapping("/api/v1/lcms/questions/{questionId}")
    public ApiResponse<SaveQuestionResponse> update(
            @PathVariable String questionId,
            @RequestBody SaveQuestionRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok(
                "Question updated",
                questionBankAuthoringService.update(access.tenantId(), questionId, body),
                request
        );
    }

    @DeleteMapping("/api/v1/lcms/questions/{questionId}")
    public ApiResponse<DeleteQuestionResponse> delete(
            @PathVariable String questionId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok(
                "Question archived",
                questionBankAuthoringService.archive(access.tenantId(), questionId),
                request
        );
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
