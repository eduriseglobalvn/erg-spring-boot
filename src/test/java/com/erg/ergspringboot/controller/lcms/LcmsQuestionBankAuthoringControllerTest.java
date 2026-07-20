package com.erg.ergspringboot.controller.lcms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuestionBankAuthoringRequests.SaveQuestionRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.DeleteQuestionResponse;
import com.erg.ergspringboot.common.response.quiz.QuestionBankAuthoringResponses.SaveQuestionResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.quiz.authoring.QuestionBankAuthoringService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LcmsQuestionBankAuthoringControllerTest {

    @Mock
    private QuestionBankAuthoringService questionBankAuthoringService;

    private LcmsQuestionBankAuthoringController controller;

    @BeforeEach
    void setUp() {
        controller = new LcmsQuestionBankAuthoringController(questionBankAuthoringService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ErgPrincipal(
                                "author-1",
                                "session-1",
                                "tenant-a",
                                List.of("admin"),
                                List.of("lcms.quiz.create"),
                                List.of(),
                                List.of("lcms"),
                                "admin",
                                "tenant",
                                Map.of()
                        ),
                        null,
                        List.of()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPassesPrincipalTenantAndUser() {
        SaveQuestionRequest body = requestBody();
        SaveQuestionResponse saved = new SaveQuestionResponse("question-1", "ready");
        when(questionBankAuthoringService.create("tenant-a", "author-1", body)).thenReturn(saved);

        ApiResponse<SaveQuestionResponse> response = controller.create(body, request("/api/v1/lcms/questions"));

        assertThat(response.data()).isSameAs(saved);
        verify(questionBankAuthoringService).create("tenant-a", "author-1", body);
    }

    @Test
    void updatePassesPrincipalTenant() {
        SaveQuestionRequest body = requestBody();
        SaveQuestionResponse saved = new SaveQuestionResponse("question-1", "reviewing");
        when(questionBankAuthoringService.update("tenant-a", "question-1", body)).thenReturn(saved);

        ApiResponse<SaveQuestionResponse> response = controller.update("question-1", body, request("/api/v1/lcms/questions/question-1"));

        assertThat(response.data()).isSameAs(saved);
        verify(questionBankAuthoringService).update("tenant-a", "question-1", body);
    }

    @Test
    void deleteArchivesQuestionForTenant() {
        DeleteQuestionResponse deleted = new DeleteQuestionResponse("question-1", true);
        when(questionBankAuthoringService.archive("tenant-a", "question-1")).thenReturn(deleted);

        ApiResponse<DeleteQuestionResponse> response = controller.delete("question-1", request("/api/v1/lcms/questions/question-1"));

        assertThat(response.data()).isSameAs(deleted);
        verify(questionBankAuthoringService).archive("tenant-a", "question-1");
    }

    private SaveQuestionRequest requestBody() {
        return new SaveQuestionRequest(
                "global",
                null,
                "ic3-gs6",
                "ic3-gs6-level-1",
                "ic3-hardware",
                null,
                "multiple-choice",
                "What is a CPU?",
                "Identify hardware basics",
                "core",
                "ready",
                List.of("hardware"),
                List.of(),
                "a",
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Request-ID", "req-1");
        return request;
    }
}
