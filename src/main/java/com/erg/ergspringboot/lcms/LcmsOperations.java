package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.*;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public LCMS command facade. It exposes only module-owned request/response contracts. */
@Service
public class LcmsOperations {
    private final LcmsCommandPort commands;

    @Autowired
    public LcmsOperations(LcmsCommandPort commands) {
        this.commands = commands;
    }

    public SaveQuestionResponse createQuestion(String tenantId, String userId, SaveQuestionRequest request) { return commands.createQuestion(tenantId, userId, request); }
    public SaveQuestionResponse updateQuestion(String tenantId, String questionId, SaveQuestionRequest request) { return commands.updateQuestion(tenantId, questionId, request); }
    public DeleteQuestionResponse archiveQuestion(String tenantId, String questionId) { return commands.archiveQuestion(tenantId, questionId); }
    public CreateQuizResponse createQuiz(String tenantId, String userId, String idempotencyKey, CreateQuizRequest request) { return commands.createQuiz(tenantId, userId, idempotencyKey, request); }
    public SaveDraftResponse saveDraft(String tenantId, String quizId, SaveDraftRequest request) { return commands.saveDraft(tenantId, quizId, request); }
    public SaveDraftResponse upsertSlide(String tenantId, String quizId, SlideUpsertRequest request) { return commands.upsertSlide(tenantId, quizId, request); }
    public SaveDraftResponse reorder(String tenantId, String quizId, ReorderRequest request) { return commands.reorder(tenantId, quizId, request); }
    public ValidationResultResponse validateQuiz(String tenantId, String quizId) { return commands.validateQuiz(tenantId, quizId); }
    public PublishResponse publishQuiz(String tenantId, String quizId, String userId, String idempotencyKey, PublishRequest request) { return commands.publishQuiz(tenantId, quizId, userId, idempotencyKey, request); }
    public ForkResponse forkQuizVersion(String tenantId, String quizId, String versionId, String userId) { return commands.forkQuizVersion(tenantId, quizId, versionId, userId); }
    public DeleteQuizResponse deleteQuiz(String tenantId, String quizId) { return commands.deleteQuiz(tenantId, quizId); }
    public MediaPresignResponse presignMedia(String tenantId, String userId, MediaPresignRequest request) { return commands.presignMedia(tenantId, userId, request); }
    public long maxMediaUploadBytes() { return commands.maxMediaUploadBytes(); }
    public boolean storeSignedMediaUpload(String uploadUrl, String tenantId, String mimeType, byte[] bytes) { return commands.storeSignedMediaUpload(uploadUrl, tenantId, mimeType, bytes); }
    public MediaCommitResponse commitMedia(String tenantId, MediaCommitRequest request) { return commands.commitMedia(tenantId, request); }
    public LcmsQuizBundleResponse exportActiveBundle(String tenantId, String quizId) { return commands.exportActiveBundle(tenantId, quizId); }
    public LcmsQuizBundleResponse exportVersionBundle(String tenantId, String quizVersionId) { return commands.exportVersionBundle(tenantId, quizVersionId); }
    public LcmsQuizPackageManifest exportPackageManifest(String tenantId, String packageId) { return commands.exportPackageManifest(tenantId, packageId); }
    public LcmsQuizPackageUpdateFeedResponse packageUpdates(String tenantId, String quizId, String releaseChannel, Integer currentVersionNumber, String runtimeVersion) {
        return commands.packageUpdates(tenantId, quizId, releaseChannel, currentVersionNumber, runtimeVersion);
    }
    public LcmsQuizBundleResponse.RevokeResponse revokeBundle(String tenantId, String packageId, String reason) { return commands.revokeBundle(tenantId, packageId, reason); }
}
