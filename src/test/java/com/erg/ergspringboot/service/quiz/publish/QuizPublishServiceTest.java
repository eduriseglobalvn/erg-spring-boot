package com.erg.ergspringboot.service.quiz.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizQuestionRepository;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionQuestion;
import com.erg.ergspringboot.model.quiz.version.QuizVersionSection;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizPackageRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionSectionRepository;
import com.erg.ergspringboot.service.quiz.assessment.AssessmentCurriculumBridge;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class QuizPublishServiceTest {

    @Test
    void publishEvictsRuntimePackageCacheForTenantQuiz() {
        QuizRepository quizRepository = mock(QuizRepository.class);
        QuestionRepository bankQuestionRepository = mock(QuestionRepository.class);
        QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
        QuizDraftRepository quizDraftRepository = mock(QuizDraftRepository.class);
        QuizVersionRepository quizVersionRepository = mock(QuizVersionRepository.class);
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
        when(compileService.compile(eq(draft.getSettings()), eq(draft.getTree()), eq(Map.of()), eq(Map.of()))).thenReturn(compiled);
        when(validationService.validate(compiled)).thenReturn(List.of());
        when(quizVersionRepository.findTopByQuizIdAndTenantIdOrderByVersionNumberDesc("quiz-1", "tenant-a")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("canonical-json");
        when(packageSigner.contentHash("canonical-json")).thenReturn("sha256:abcdef1234567890");
        when(packageSigner.sign("canonical-json")).thenReturn("signature-1");
        when(packageSigner.publicKeyId()).thenReturn("key-1");
        when(assessmentCurriculumBridge.snapshotForQuiz("tenant-a", quiz)).thenReturn(Map.of(
                "subject", Map.of("id", "subject-1", "label", "IC3"),
                "level", Map.of("id", "level-1", "label", "GS6 Level 1"),
                "topic", Map.of("id", "topic-1", "label", "Hardware")
        ));
        when(quizVersionRepository.findByQuizIdAndTenantIdAndIsActiveTrue("quiz-1", "tenant-a")).thenReturn(Optional.empty());
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
                cacheManager
        );

        var response = service.publish(
                "tenant-a",
                "quiz-1",
                "author-1",
                "publish-key-1",
                new PublishRequest(7L, "server-authoritative", "v1")
        );

        assertThat(response.quizVersionId()).isEqualTo("version-1");
        assertThat(response.contentHash()).isEqualTo("sha256:abcdef1234567890");
        verify(runtimeCache).evict("tenant-a:quiz-1");
        verify(quizRepository).save(quiz);
        assertThat(quiz.getCurrentVersionId()).isEqualTo("version-1");
        assertThat(quiz.getPackageHash()).isEqualTo("sha256:abcdef1234567890");
        ArgumentCaptor<QuizVersion> versionCaptor = ArgumentCaptor.forClass(QuizVersion.class);
        verify(quizVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getAuthoringSnapshot())
                .containsEntry("curriculumSnapshot", Map.of(
                        "subject", Map.of("id", "subject-1", "label", "IC3"),
                        "level", Map.of("id", "level-1", "label", "GS6 Level 1"),
                        "topic", Map.of("id", "topic-1", "label", "Hardware")
                ));
    }
}
