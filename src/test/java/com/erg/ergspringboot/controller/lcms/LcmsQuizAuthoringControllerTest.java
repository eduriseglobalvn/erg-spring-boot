package com.erg.ergspringboot.controller.lcms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.DeleteQuizResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ForkResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.PublishResponse;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.ValidationResultResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.quiz.authoring.QuizAuthoringService;
import com.erg.ergspringboot.service.quiz.authoring.QuizDraftService;
import com.erg.ergspringboot.service.quiz.publish.QuizPublishService;
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
class LcmsQuizAuthoringControllerTest {

    @Mock
    private QuizAuthoringService quizAuthoringService;

    @Mock
    private QuizDraftService quizDraftService;

    @Mock
    private QuizPublishService quizPublishService;

    private LcmsQuizAuthoringController controller;

    @BeforeEach
    void setUp() {
        controller = new LcmsQuizAuthoringController(quizAuthoringService, quizDraftService, quizPublishService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ErgPrincipal(
                                "author-1",
                                "session-1",
                                "tenant-a",
                                List.of("admin"),
                                List.of("lms.quiz.create"),
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
    void validateDelegatesToPublishService() {
        ValidationResultResponse validation = new ValidationResultResponse(true, List.of());
        when(quizPublishService.validate("tenant-a", "quiz-1")).thenReturn(validation);

        ApiResponse<ValidationResultResponse> response = controller.validate("quiz-1", request("/api/v1/lcms/quizzes/quiz-1/validate"));

        assertThat(response.data()).isSameAs(validation);
        verify(quizPublishService).validate("tenant-a", "quiz-1");
    }

    @Test
    void publishPassesPrincipalAndIdempotencyKey() {
        PublishRequest requestBody = new PublishRequest(7L, "client-first", "v7");
        PublishResponse published = new PublishResponse("version-1", "v7", 7, "package-1", "hash-1");
        when(quizPublishService.publish("tenant-a", "quiz-1", "author-1", "publish-key-1", requestBody))
                .thenReturn(published);

        ApiResponse<PublishResponse> response = controller.publish(
                "quiz-1",
                requestBody,
                "publish-key-1",
                request("/api/v1/lcms/quizzes/quiz-1/publish")
        );

        assertThat(response.data()).isSameAs(published);
        verify(quizPublishService).publish("tenant-a", "quiz-1", "author-1", "publish-key-1", requestBody);
    }

    @Test
    void forkVersionPassesPrincipalUser() {
        ForkResponse fork = new ForkResponse("draft-2", 0, "v1");
        when(quizPublishService.fork("tenant-a", "quiz-1", "version-1", "author-1")).thenReturn(fork);

        ApiResponse<ForkResponse> response = controller.forkVersion(
                "quiz-1",
                "version-1",
                request("/api/v1/lcms/quizzes/quiz-1/versions/version-1/fork")
        );

        assertThat(response.data()).isSameAs(fork);
        verify(quizPublishService).fork("tenant-a", "quiz-1", "version-1", "author-1");
    }

    @Test
    void deletePassesPrincipalTenant() {
        DeleteQuizResponse deleted = new DeleteQuizResponse("quiz-1", true);
        when(quizAuthoringService.delete("tenant-a", "quiz-1")).thenReturn(deleted);

        ApiResponse<DeleteQuizResponse> response = controller.delete(
                "quiz-1",
                request("/api/v1/lcms/quizzes/quiz-1")
        );

        assertThat(response.data()).isSameAs(deleted);
        verify(quizAuthoringService).delete("tenant-a", "quiz-1");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Request-ID", "req-1");
        return request;
    }
}
