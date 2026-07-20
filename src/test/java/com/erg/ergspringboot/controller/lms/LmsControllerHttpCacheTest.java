package com.erg.ergspringboot.controller.lms;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ContentScope;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizResponse;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LmsControllerHttpCacheTest {

    @BeforeEach
    void authenticateRequestFixture() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new ErgPrincipal("test-user", "session", "tenant-1", List.of(), List.of("*"), List.of(), List.of(), "erg", "user", Map.of()),
                        null,
                        List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void quizPackageSupportsETagCacheControlAndConditionalGet() throws Exception {
        LmsRuntimeService runtimeService = org.mockito.Mockito.mock(LmsRuntimeService.class);
        when(runtimeService.getQuizPackage(eq("quiz-1"), any())).thenReturn(packageResponse());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LmsController(
                runtimeService, new com.erg.ergspringboot.lms.LmsOperations(runtimeService))).build();

        String eTag = mockMvc.perform(get("/api/v1/lms/quizzes/quiz-1/package")
                        .header("X-Request-ID", "req-cache-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"hash-1\""))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(jsonPath("$.data.contentHash", notNullValue()))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mockMvc.perform(get("/api/v1/lms/quizzes/quiz-1/package")
                        .header("If-None-Match", eTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"hash-1\""));
    }

    private QuizPackageResponse packageResponse() {
        QuizResponse quiz = new QuizResponse(
                "quiz-1",
                new ContentScope(null, null),
                "Quiz",
                null,
                null,
                null,
                List.of(),
                List.of(),
                "published",
                1,
                "hash-1",
                OffsetDateTime.now(),
                OffsetDateTime.now());
        return new QuizPackageResponse(
                1,
                "hash-1",
                "hash-1",
                "signature",
                "server-authoritative",
                new QuizDetailResponse(quiz, List.of(), Map.of(), Map.of(), Map.of()),
                Map.of());
    }
}
