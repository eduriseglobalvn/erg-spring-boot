package com.erg.ergspringboot.lcms.internal.command;

import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.*;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.*;
import com.erg.ergspringboot.lcms.LcmsCommandPort;
import com.erg.ergspringboot.lcms.LcmsQuizBundleResponse;
import com.erg.ergspringboot.lcms.LcmsQuizPackageManifest;
import com.erg.ergspringboot.lcms.LcmsQuizPackageUpdateFeedResponse;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.bundle.PortableQuizBundleService;
import com.erg.ergspringboot.service.quiz.media.MediaService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Owns the LCMS command boundary while legacy quiz services are strangled inward. */
@Primary
@Service
public class LcmsCommandUseCase implements LcmsCommandPort {
    private final QuestionBankAuthoringService questions;
    private final QuizAuthoringService quizzes;
    private final QuizDraftService drafts;
    private final QuizPublishService publishing;
    private final MediaService media;
    private final PortableQuizBundleService bundles;

    @Autowired
    public LcmsCommandUseCase(
            QuestionBankAuthoringService questions,
            QuizAuthoringService quizzes,
            QuizDraftService drafts,
            QuizPublishService publishing,
            MediaService media,
            PortableQuizBundleService bundles) {
        this.questions = questions;
        this.quizzes = quizzes;
        this.drafts = drafts;
        this.publishing = publishing;
        this.media = media;
        this.bundles = bundles;
    }

    @Override public SaveQuestionResponse createQuestion(String tenantId, String userId, SaveQuestionRequest request) { return questions.create(tenantId, userId, request); }
    @Override public SaveQuestionResponse updateQuestion(String tenantId, String questionId, SaveQuestionRequest request) { return questions.update(tenantId, questionId, request); }
    @Override public DeleteQuestionResponse archiveQuestion(String tenantId, String questionId) { return questions.archive(tenantId, questionId); }
    @Override public CreateQuizResponse createQuiz(String tenantId, String userId, String idempotencyKey, CreateQuizRequest request) { return quizzes.create(tenantId, userId, idempotencyKey, request); }
    @Override public SaveDraftResponse saveDraft(String tenantId, String quizId, SaveDraftRequest request) { return drafts.saveDraft(tenantId, quizId, request); }
    @Override public SaveDraftResponse upsertSlide(String tenantId, String quizId, SlideUpsertRequest request) { return drafts.upsertSlide(tenantId, quizId, request); }
    @Override public SaveDraftResponse reorder(String tenantId, String quizId, ReorderRequest request) { return drafts.reorder(tenantId, quizId, request); }
    @Override public ValidationResultResponse validateQuiz(String tenantId, String quizId) { return publishing.validate(tenantId, quizId); }
    @Override public PublishResponse publishQuiz(String tenantId, String quizId, String userId, String idempotencyKey, PublishRequest request) { return publishing.publish(tenantId, quizId, userId, idempotencyKey, request); }
    @Override public ForkResponse forkQuizVersion(String tenantId, String quizId, String versionId, String userId) { return publishing.fork(tenantId, quizId, versionId, userId); }
    @Override public DeleteQuizResponse deleteQuiz(String tenantId, String quizId) { return quizzes.delete(tenantId, quizId); }
    @Override public MediaPresignResponse presignMedia(String tenantId, String userId, MediaPresignRequest request) { return media.presign(tenantId, userId, request); }
    @Override public long maxMediaUploadBytes() { return MediaService.MAX_IMAGE_BYTES; }
    @Override public boolean storeSignedMediaUpload(String uploadUrl, String tenantId, String mimeType, byte[] bytes) { return media.storeSignedUpload(uploadUrl, tenantId, mimeType, bytes); }
    @Override public MediaCommitResponse commitMedia(String tenantId, MediaCommitRequest request) { return media.commit(tenantId, request); }
    @Override public LcmsQuizBundleResponse exportActiveBundle(String tenantId, String quizId) {
        return LcmsQuizBundleResponse.fromLegacy(bundles.exportActive(tenantId, quizId));
    }
    @Override public LcmsQuizBundleResponse exportVersionBundle(String tenantId, String quizVersionId) {
        return LcmsQuizBundleResponse.fromLegacy(bundles.exportVersion(tenantId, quizVersionId));
    }
    @Override public LcmsQuizPackageManifest exportPackageManifest(String tenantId, String packageId) {
        return bundles.manifest(tenantId, packageId);
    }
    @Override public LcmsQuizPackageUpdateFeedResponse packageUpdates(
            String tenantId,
            String quizId,
            String releaseChannel,
            Integer currentVersionNumber,
            String runtimeVersion) {
        return bundles.updates(tenantId, quizId, releaseChannel, currentVersionNumber, runtimeVersion);
    }
    @Override public LcmsQuizBundleResponse.RevokeResponse revokeBundle(String tenantId, String packageId, String reason) {
        var revoked = bundles.revoke(tenantId, packageId, reason);
        String etag = revoked.getContentHash() == null || revoked.getContentHash().isBlank()
                ? revoked.getId()
                : revoked.getContentHash();
        return new LcmsQuizBundleResponse.RevokeResponse(revoked.getId(), etag, revoked.getRevokedAt());
    }
}
