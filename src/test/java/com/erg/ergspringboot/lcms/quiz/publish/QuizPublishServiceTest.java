package com.erg.ergspringboot.lcms.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.content.lifecycle.ContentLifecyclePort;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionSection;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionSectionRepository;
import com.erg.ergspringboot.lcms.internal.quiz.assessment.AssessmentCurriculumBridge;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class QuizPublishServiceTest {

    private QuizVersion previouslyActive;
    private QuizVersionRepository versions;

    @Test
    void republishDeactivatesTheOldVersionAndFlushesBeforeInsertingTheNewOne() {
        previouslyActive = new QuizVersion();
        previouslyActive.setIsActive(true);

        publishAndCapture("test", "server-authoritative", false);

        var order = org.mockito.Mockito.inOrder(versions);
        order.verify(versions).saveAndFlush(previouslyActive);
        order.verify(versions).save(any(QuizVersion.class));
        assertThat(previouslyActive.getIsActive()).isFalse();
    }

    @Test
    void publishEvictsRuntimePackageCacheForTenantQuiz() {
        publishAndCapture(null, "server-authoritative", true);
    }

    @Test
    void testQuizzesArePublishedServerAuthoritativeEvenWhenClientFirstIsRequested() {
        QuizVersion version = publishAndCapture("test", "client-first", false);
        assertThat(version.getGradingMode()).isEqualTo("server-authoritative");
    }

    @Test
    void trainingQuizzesKeepTheRequestedClientFirstMode() {
        QuizVersion version = publishAndCapture("train", "client-first", false);
        assertThat(version.getGradingMode()).isEqualTo("client-first");
    }

    private QuizVersion publishAndCapture(String kind, String gradingMode, boolean verifyCache) {
        QuizRepository quizRepository = mock(QuizRepository.class);
        QuestionRepository bankQuestionRepository = mock(QuestionRepository.class);
        QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
        QuizDraftRepository quizDraftRepository = mock(QuizDraftRepository.class);
        QuizVersionRepository quizVersionRepository = mock(QuizVersionRepository.class);
        versions = quizVersionRepository;
        QuizVersionSectionRepository sectionRepository = mock(QuizVersionSectionRepository.class);
        QuizVersionQuestionRepository questionRepository = mock(QuizVersionQuestionRepository.class);
        QuizPackageRepository packageRepository = mock(QuizPackageRepository.class);
        QuizCompileService compileService = mock(QuizCompileService.class);
        QuizValidationService validationService = mock(QuizValidationService.class);
        QuizPackageSigner packageSigner = mock(QuizPackageSigner.class);
        AssessmentCurriculumBridge assessmentCurriculumBridge = mock(AssessmentCurriculumBridge.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CacheManager cacheManager = mock(CacheManager.class);
        Cache runtimeCache = mock(Cache.class);

        Quiz quiz = new Quiz();
        quiz.setId("quiz-1");
        quiz.setTenantId("tenant-a");
        quiz.setTitle("Quiz 1");
        quiz.setSubjectId("subject-1");
        quiz.setLevelId("level-1");
        quiz.setCategoryId("topic-1");
        quiz.setKind(kind);

        QuizDraft draft = new QuizDraft();
        draft.setId("draft-1");
        draft.setQuizId("quiz-1");
        draft.setTenantId("tenant-a");
        draft.setDocVersion(7L);
        draft.setSettings(Map.of("mode", "testing"));
        draft.setPlayerTemplate(Map.of());
        draft.setTree(List.of(Map.of("slides", List.of(Map.of("questionId", "bank-question-1", "type", "multiple-choice")))));

        CompiledQuiz compiled = new CompiledQuiz(
                List.of(new CompiledQuiz.CompiledSection("section-1", "Section 1", 0)),
                List.of(new CompiledQuiz.CompiledQuestion(
                        "question-1",
                        "slide-1",
                        "section-1",
                        "bank-question-1",
                        "single_choice",
                        "singleChoice",
                        BigDecimal.ONE,
                        true,
                        "exact",
                        0,
                        Map.of("title", "Question 1"),
                        Map.of("correctChoiceIds", List.of("a"))
                )),
                Map.of(
                        "info", Map.of("title", "Quiz 1"),
                        "sections", List.of(Map.of("id", "section-1"))
                ),
                Map.of("question-1", Map.of("correctChoiceIds", List.of("a")))
        );

        when(quizRepository.findByIdAndTenantId("quiz-1", "tenant-a")).thenReturn(Optional.of(quiz));
        when(quizVersionRepository.findByQuizIdAndTenantIdOrderByVersionNumberDesc("quiz-1", "tenant-a")).thenReturn(List.of());
        when(quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc("quiz-1", "tenant-a")).thenReturn(Optional.of(draft));
        when(bankQuestionRepository.findByTenantIdAndIdIn("tenant-a", List.of("bank-question-1"))).thenReturn(List.of());
        when(quizQuestionRepository.findByTenantIdAndQuizIdOrderByOrderIndexAsc("tenant-a", "quiz-1")).thenReturn(List.of());
        when(compileService.compile(eq(draft.getSettings()), eq(draft.getTree()), eq(Map.of()), eq(Map.of()), eq(draft.getPlayerTemplate()))).thenReturn(compiled);
        when(validationService.validate(compiled)).thenReturn(List.of());
        when(quizVersionRepository.findTopByQuizIdAndTenantIdOrderByVersionNumberDesc("quiz-1", "tenant-a")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = invocation.getArgument(0);
            return payload.containsKey("runtimeQuiz") ? "legacy-hash-json" : "canonical-package-json";
        });
        when(packageSigner.contentHash(anyString())).thenAnswer(invocation ->
                "canonical-package-json".equals(invocation.getArgument(0))
                        ? "sha256:abcdef1234567890"
                        : "sha256:legacy0000000000");
        when(packageSigner.sign("canonical-package-json")).thenReturn("signature-1");
        when(packageSigner.publicKeyId()).thenReturn("key-1");
        when(assessmentCurriculumBridge.snapshotForQuiz("tenant-a", quiz)).thenReturn(Map.of(
                "subject", Map.of("id", "subject-1", "label", "IC3"),
                "level", Map.of("id", "level-1", "label", "GS6 Level 1"),
                "topic", Map.of("id", "topic-1", "label", "Hardware")
        ));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a")).thenReturn(Optional.ofNullable(previouslyActive));
        when(sectionRepository.save(any(QuizVersionSection.class))).thenAnswer(invocation -> {
            QuizVersionSection section = invocation.getArgument(0);
            section.setId("section-db-1");
            return section;
        });
        when(questionRepository.save(any(QuizVersionQuestion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizVersionRepository.save(any(QuizVersion.class))).thenAnswer(invocation -> {
            QuizVersion version = invocation.getArgument(0);
            version.setId("version-1");
            return version;
        });
        when(packageRepository.save(any(QuizPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cacheManager.getCache("quizRuntimePackage")).thenReturn(runtimeCache);

        ContentLifecyclePort lifecycle = mock(ContentLifecyclePort.class);
        // The lifecycle reads a "published" status as already published and would skip the DRAFT -> PUBLISHED transition.
        AtomicReference<String> statusSeenByLifecycle = new AtomicReference<>("unset");
        doAnswer(invocation -> {
            statusSeenByLifecycle.set(quiz.getStatus());
            return null;
        }).when(lifecycle).recordPublishedSnapshot(anyString(), anyString(), anyString(), anyString());
        QuizPublishService service = new QuizPublishService(
                quizRepository,
                bankQuestionRepository,
                quizQuestionRepository,
                quizDraftRepository,
                quizVersionRepository,
                sectionRepository,
                questionRepository,
                packageRepository,
                compileService,
                validationService,
                packageSigner,
                assessmentCurriculumBridge,
                objectMapper,
                cacheManager,
                lifecycle
        );

        var response = service.publish(
                "tenant-a",
                "quiz-1",
                "author-1",
                "publish-key-1",
                new PublishRequest(7L, gradingMode, "v1")
        );

        ArgumentCaptor<QuizVersion> versionCaptor = ArgumentCaptor.forClass(QuizVersion.class);
        verify(quizVersionRepository).save(versionCaptor.capture());
        if (!verifyCache) return versionCaptor.getValue();
        assertThat(response.quizVersionId()).isEqualTo("version-1");
        assertThat(response.contentHash()).isEqualTo("sha256:abcdef1234567890");
        verify(packageSigner).contentHash("canonical-package-json");
        verify(packageSigner).sign("canonical-package-json");
        verify(runtimeCache).clear();
        verify(quizRepository).save(quiz);
        verify(lifecycle).recordPublishedSnapshot("tenant-a", "quiz-1", "author-1", "publish-key-1");
        assertThat(statusSeenByLifecycle.get()).isNotEqualTo("unset").isNotEqualTo("published");
        assertThat(quiz.getCurrentVersionId()).isEqualTo("version-1");
        assertThat(quiz.getPackageHash()).isEqualTo("sha256:abcdef1234567890");
        assertThat(versionCaptor.getValue().getAuthoringSnapshot())
                .containsEntry("curriculumSnapshot", Map.of(
                        "subject", Map.of("id", "subject-1", "label", "IC3"),
                        "level", Map.of("id", "level-1", "label", "GS6 Level 1"),
                        "topic", Map.of("id", "topic-1", "label", "Hardware")
                ));
        return versionCaptor.getValue();
    }
}
