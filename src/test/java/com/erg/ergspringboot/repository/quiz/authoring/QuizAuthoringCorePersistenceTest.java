package com.erg.ergspringboot.repository.quiz.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Phase 0.2 proof: the app boots under {@code hibernate.hbm2ddl.auto=validate} against the V23
 * migration (so the new lms_quizzes columns + quiz_drafts table match their entities), and a quiz
 * plus its working draft round-trip through the repositories.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class QuizAuthoringCorePersistenceTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizDraftRepository quizDraftRepository;

    @Test
    void quizAndDraftRoundTripWithAuthoringColumns() {
        String tenantId = "tenant-quiz-authoring";

        Quiz quiz = new Quiz();
        quiz.setTenantId(tenantId);
        quiz.setPortal("lcms");
        quiz.setTitle("Fractions practice");
        quiz.setSubtitle("TRAINING");
        quiz.setKind("train");
        quiz.setSubjectId("sub_ic3");
        quiz.setLevelId("lvl_ic3_gs6_l1");
        quiz.setCategoryId("top_ic3_safety");
        quiz.setStatus("draft");
        quiz.setOwnerUserId("seed.quiz.demo");
        quiz.setContentScope(Map.of("type", "school", "schoolId", "school-001"));
        Quiz savedQuiz = quizRepository.saveAndFlush(quiz);

        QuizDraft draft = new QuizDraft();
        draft.setQuizId(savedQuiz.getId());
        draft.setTenantId(tenantId);
        draft.setSettings(Map.of("info", Map.of("title", "Fractions practice")));
        draft.setPlayerTemplate(Map.of("theme", "default"));
        draft.setTree(List.of(Map.of("groupId", "g1", "slides", List.of(Map.of("slideId", "s1")))));
        draft.setSchemaVersion(1);
        draft.setDocVersion(0L);
        draft.setUpdatedBy("seed.quiz.demo");
        QuizDraft savedDraft = quizDraftRepository.saveAndFlush(draft);

        savedQuiz.setLatestDraftId(savedDraft.getId());
        quizRepository.saveAndFlush(savedQuiz);

        Quiz reloaded = quizRepository.findById(savedQuiz.getId()).orElseThrow();
        assertThat(reloaded.getPortal()).isEqualTo("lcms");
        assertThat(reloaded.getSubtitle()).isEqualTo("TRAINING");
        assertThat(reloaded.getCategoryId()).isEqualTo("top_ic3_safety");
        assertThat(reloaded.getContentScope()).containsEntry("schoolId", "school-001");
        assertThat(reloaded.getLatestDraftId()).isEqualTo(savedDraft.getId());

        QuizDraft latestDraft = quizDraftRepository
                .findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(savedQuiz.getId(), tenantId)
                .orElseThrow();
        assertThat(latestDraft.getDocVersion()).isZero();
        assertThat(latestDraft.getSettings()).containsKey("info");
    }

    static boolean postgresProofDatabaseAvailable() {
        if (firstText(
                System.getProperty("erg.test.postgres.url"),
                System.getenv("ERG_TEST_POSTGRES_URL")
        ) != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
