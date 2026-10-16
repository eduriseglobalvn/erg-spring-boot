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

/** Public command boundary for LCMS authoring, publishing, media and export. */
public interface LcmsCommandPort {
    SaveQuestionResponse createQuestion(String tenantId, String userId, SaveQuestionRequest request);
    SaveQuestionResponse updateQuestion(String tenantId, String questionId, SaveQuestionRequest request);
    DeleteQuestionResponse archiveQuestion(String tenantId, String questionId);
    CreateQuizResponse createQuiz(String tenantId, String userId, String idempotencyKey, CreateQuizRequest request);
    SaveDraftResponse saveDraft(String tenantId, String quizId, SaveDraftRequest request);
    SaveDraftResponse upsertSlide(String tenantId, String quizId, SlideUpsertRequest request);
    SaveDraftResponse reorder(String tenantId, String quizId, ReorderRequest request);
    ValidationResultResponse validateQuiz(String tenantId, String quizId);
    PublishResponse publishQuiz(String tenantId, String quizId, String userId, String idempotencyKey, PublishRequest request);
    ForkResponse forkQuizVersion(String tenantId, String quizId, String versionId, String userId);
    DeleteQuizResponse deleteQuiz(String tenantId, String quizId);
    MediaPresignResponse presignMedia(String tenantId, String userId, MediaPresignRequest request);
    long maxMediaUploadBytes();
    boolean storeSignedMediaUpload(String uploadUrl, String tenantId, String mimeType, byte[] bytes);
    MediaCommitResponse commitMedia(String tenantId, MediaCommitRequest request);
    LcmsQuizBundleResponse exportActiveBundle(String tenantId, String quizId);
    LcmsQuizBundleResponse exportVersionBundle(String tenantId, String quizVersionId);
    LcmsQuizPackageManifest exportPackageManifest(String tenantId, String packageId);
    LcmsQuizPackageUpdateFeedResponse packageUpdates(
            String tenantId,
            String quizId,
            String releaseChannel,
            Integer currentVersionNumber,
            String runtimeVersion);
    LcmsQuizBundleResponse.RevokeResponse revokeBundle(String tenantId, String packageId, String reason);
}
