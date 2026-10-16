package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.CreateQuizRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.ReorderRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SaveDraftRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SlideUpsertRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.CreateQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.DeleteQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ForkResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.PublishResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.SaveDraftResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationResultResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
import com.erg.ergspringboot.service.quiz.media.MediaService;
import com.erg.ergspringboot.service.quiz.bundle.PortableQuizBundleService;
import org.junit.jupiter.api.Test;

class LcmsOperationsTest {
    @Test
    void delegatesQuestionAuthoringThroughLcmsBoundary() {
        QuestionBankAuthoringService legacy = mock(QuestionBankAuthoringService.class);
        SaveQuestionRequest request = mock(SaveQuestionRequest.class);
        SaveQuestionResponse expected = mock(SaveQuestionResponse.class);
        when(legacy.create("tenant-1", "user-1", request)).thenReturn(expected);

        assertThat(new LcmsOperations(LcmsCommandCompatibility.fromServices(
                legacy,
                mock(QuizAuthoringService.class),
                mock(QuizDraftService.class),
                mock(QuizPublishService.class),
                mock(MediaService.class),
                mock(PortableQuizBundleService.class)
        )).createQuestion("tenant-1", "user-1", request))
                .isSameAs(expected);
    }

    @Test
    void delegatesQuizAuthoringThroughLcmsBoundary() {
        QuizAuthoringService authoring = mock(QuizAuthoringService.class);
        QuizDraftService drafts = mock(QuizDraftService.class);
        QuizPublishService publishing = mock(QuizPublishService.class);
        CreateQuizRequest createRequest = mock(CreateQuizRequest.class);
        CreateQuizResponse created = mock(CreateQuizResponse.class);
        SaveDraftRequest draftRequest = mock(SaveDraftRequest.class);
        SaveDraftResponse saved = mock(SaveDraftResponse.class);
        SlideUpsertRequest slideRequest = mock(SlideUpsertRequest.class);
        ReorderRequest reorderRequest = mock(ReorderRequest.class);
        PublishRequest publishRequest = mock(PublishRequest.class);
        ValidationResultResponse validation = mock(ValidationResultResponse.class);
        PublishResponse published = mock(PublishResponse.class);
        ForkResponse forked = mock(ForkResponse.class);
        DeleteQuizResponse deleted = mock(DeleteQuizResponse.class);

        when(authoring.create("tenant-1", "user-1", "create-key", createRequest)).thenReturn(created);
        when(drafts.saveDraft("tenant-1", "quiz-1", draftRequest)).thenReturn(saved);
        when(drafts.upsertSlide("tenant-1", "quiz-1", slideRequest)).thenReturn(saved);
        when(drafts.reorder("tenant-1", "quiz-1", reorderRequest)).thenReturn(saved);
        when(publishing.validate("tenant-1", "quiz-1")).thenReturn(validation);
        when(publishing.publish("tenant-1", "quiz-1", "user-1", "publish-key", publishRequest)).thenReturn(published);
        when(publishing.fork("tenant-1", "quiz-1", "version-1", "user-1")).thenReturn(forked);
        when(authoring.delete("tenant-1", "quiz-1")).thenReturn(deleted);

        LcmsOperations operations = new LcmsOperations(LcmsCommandCompatibility.fromServices(
                mock(QuestionBankAuthoringService.class), authoring, drafts, publishing,
                mock(MediaService.class), mock(PortableQuizBundleService.class)));

        assertThat(operations.createQuiz("tenant-1", "user-1", "create-key", createRequest)).isSameAs(created);
        assertThat(operations.saveDraft("tenant-1", "quiz-1", draftRequest)).isSameAs(saved);
        assertThat(operations.upsertSlide("tenant-1", "quiz-1", slideRequest)).isSameAs(saved);
        assertThat(operations.reorder("tenant-1", "quiz-1", reorderRequest)).isSameAs(saved);
        assertThat(operations.validateQuiz("tenant-1", "quiz-1")).isSameAs(validation);
        assertThat(operations.publishQuiz("tenant-1", "quiz-1", "user-1", "publish-key", publishRequest)).isSameAs(published);
        assertThat(operations.forkQuizVersion("tenant-1", "quiz-1", "version-1", "user-1")).isSameAs(forked);
        assertThat(operations.deleteQuiz("tenant-1", "quiz-1")).isSameAs(deleted);
    }

    @Test
    void delegatesMediaOperationsThroughLcmsBoundary() {
        MediaService media = mock(MediaService.class);
        MediaPresignRequest presignRequest = mock(MediaPresignRequest.class);
        MediaPresignResponse presigned = mock(MediaPresignResponse.class);
        MediaCommitRequest commitRequest = mock(MediaCommitRequest.class);
        MediaCommitResponse committed = mock(MediaCommitResponse.class);
        when(media.presign("tenant-1", "user-1", presignRequest)).thenReturn(presigned);
        when(media.commit("tenant-1", commitRequest)).thenReturn(committed);
        LcmsOperations operations = new LcmsOperations(LcmsCommandCompatibility.fromServices(
                mock(QuestionBankAuthoringService.class), mock(QuizAuthoringService.class),
                mock(QuizDraftService.class), mock(QuizPublishService.class), media,
                mock(PortableQuizBundleService.class)));

        assertThat(operations.presignMedia("tenant-1", "user-1", presignRequest)).isSameAs(presigned);
        assertThat(operations.commitMedia("tenant-1", commitRequest)).isSameAs(committed);
        verify(media).presign("tenant-1", "user-1", presignRequest);
        verify(media).commit("tenant-1", commitRequest);
    }
}
