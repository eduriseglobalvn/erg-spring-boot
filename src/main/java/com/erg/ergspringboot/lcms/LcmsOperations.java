package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import org.springframework.stereotype.Service;

/** Public LCMS facade for the question-authoring vertical slice. */
@Service
public class LcmsOperations {
    private final QuestionBankAuthoringService legacy;

    public LcmsOperations(QuestionBankAuthoringService legacy) {
        this.legacy = legacy;
    }

    public SaveQuestionResponse createQuestion(String tenantId, String userId, SaveQuestionRequest request) {
        return legacy.create(tenantId, userId, request);
    }

    public SaveQuestionResponse updateQuestion(String tenantId, String questionId, SaveQuestionRequest request) {
        return legacy.update(tenantId, questionId, request);
    }

    public DeleteQuestionResponse archiveQuestion(String tenantId, String questionId) {
        return legacy.archive(tenantId, questionId);
    }
}
