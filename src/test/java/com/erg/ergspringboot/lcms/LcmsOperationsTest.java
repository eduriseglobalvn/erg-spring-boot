package com.erg.ergspringboot.lcms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import org.junit.jupiter.api.Test;

class LcmsOperationsTest {
    @Test
    void delegatesQuestionAuthoringThroughLcmsBoundary() {
        QuestionBankAuthoringService legacy = mock(QuestionBankAuthoringService.class);
        SaveQuestionRequest request = mock(SaveQuestionRequest.class);
        SaveQuestionResponse expected = mock(SaveQuestionResponse.class);
        when(legacy.create("tenant-1", "user-1", request)).thenReturn(expected);

        assertThat(new LcmsOperations(legacy).createQuestion("tenant-1", "user-1", request))
                .isSameAs(expected);
    }
}
