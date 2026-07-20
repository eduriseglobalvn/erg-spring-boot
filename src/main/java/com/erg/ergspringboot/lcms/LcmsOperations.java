package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.ReorderRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SaveDraftRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SlideUpsertRequest;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.CreateQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.DeleteQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ForkResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.PublishResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.SaveDraftResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationResultResponse;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.bundle.PortableQuizBundleService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
import com.erg.ergspringboot.service.quiz.media.MediaService;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Public LCMS facade for the question-authoring vertical slice. */
@Service
public class LcmsOperations {
    private final QuestionBankAuthoringService legacy;
    private final QuizAuthoringService quizAuthoring;
    private final QuizDraftService quizDrafts;
    private final QuizPublishService quizPublishing;
    private final MediaService media;
    private final PortableQuizBundleService bundles;

    public LcmsOperations(
            QuestionBankAuthoringService legacy,
            QuizAuthoringService quizAuthoring,
            QuizDraftService quizDrafts,
            QuizPublishService quizPublishing,
            MediaService media,
            PortableQuizBundleService bundles
    ) {
        this.legacy = legacy;
        this.quizAuthoring = quizAuthoring;
        this.quizDrafts = quizDrafts;
        this.quizPublishing = quizPublishing;
        this.media = media;
        this.bundles = bundles;
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

    public CreateQuizResponse createQuiz(String tenantId, String userId, String idempotencyKey, CreateQuizRequest request) {
        return quizAuthoring.create(tenantId, userId, idempotencyKey, request);
    }

    public SaveDraftResponse saveDraft(String tenantId, String quizId, SaveDraftRequest request) {
        return quizDrafts.saveDraft(tenantId, quizId, request);
    }

    public SaveDraftResponse upsertSlide(String tenantId, String quizId, SlideUpsertRequest request) {
        return quizDrafts.upsertSlide(tenantId, quizId, request);
    }

    public SaveDraftResponse reorder(String tenantId, String quizId, ReorderRequest request) {
        return quizDrafts.reorder(tenantId, quizId, request);
    }

    public ValidationResultResponse validateQuiz(String tenantId, String quizId) {
        return quizPublishing.validate(tenantId, quizId);
    }

    public PublishResponse publishQuiz(
            String tenantId,
            String quizId,
            String userId,
            String idempotencyKey,
            PublishRequest request
    ) {
        return quizPublishing.publish(tenantId, quizId, userId, idempotencyKey, request);
    }

    public ForkResponse forkQuizVersion(String tenantId, String quizId, String versionId, String userId) {
        return quizPublishing.fork(tenantId, quizId, versionId, userId);
    }

    public DeleteQuizResponse deleteQuiz(String tenantId, String quizId) {
        return quizAuthoring.delete(tenantId, quizId);
    }

    public MediaPresignResponse presignMedia(String tenantId, String userId, MediaPresignRequest request) {
        return media.presign(tenantId, userId, request);
    }

    public MediaCommitResponse commitMedia(String tenantId, MediaCommitRequest request) {
        return media.commit(tenantId, request);
    }

    public Map<String, Object> exportActiveBundle(String tenantId, String quizId) {
        return bundles.exportActive(tenantId, quizId);
    }

    public Map<String, Object> exportVersionBundle(String tenantId, String quizVersionId) {
        return bundles.exportVersion(tenantId, quizVersionId);
    }
}
