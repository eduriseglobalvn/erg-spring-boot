package com.erg.ergspringboot.lms.internal.seed;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.PublishRequest;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.lcms.quiz.publish.QuizPublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and publishes the runtime demo quizzes built from {@link QuizDemoSeedContent}. */
@Service
public class QuizDemoBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String SEED_USER_ID = "seed.quiz.demo";
    private static final int SCHEMA_VERSION = 1;

    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizPublishService quizPublishService;

    public QuizDemoBootstrapSeedService(
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizPublishService quizPublishService
    ) {
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizPublishService = quizPublishService;
    }

    @Transactional("postgresTransactionManager")
    public void seedQuizDemos() {
        seedDemo(
                "erg-training-demo",
                "ERG on tap ky nang so (Training)",
                "Che do luyen tap tung cau",
                "train",
                "training",
                false,
                false,
                true,
                "client-first",
                "v1-training-demo"
        );
        seedDemo(
                "erg-testing-demo",
                "ERG kiem tra ky nang so (Testing)",
                "Che do kiem tra day du",
                "test",
                "testing",
                true,
                true,
                false,
                "server-authoritative",
                "v1-testing-demo"
        );
    }

    private void seedDemo(
            String quizId,
            String title,
            String subtitle,
            String kind,
            String mode,
            boolean shuffleQuestions,
            boolean shuffleChoices,
            boolean revealFeedbackPerStep,
            String gradingMode,
            String versionLabel
    ) {
        Quiz quiz = quizRepository.findByIdAndTenantId(quizId, TENANT_ID).orElseGet(() -> {
            Quiz created = new Quiz();
            created.setId(quizId);
            created.setTenantId(TENANT_ID);
            created.setPortal("lcms");
            created.setOwnerUserId(SEED_USER_ID);
            created.setSchemaVersion(SCHEMA_VERSION);
            created.setIdempotencyKey("seed:" + quizId);
            return created;
        });
        quiz.setTitle(title);
        quiz.setSubtitle(subtitle);
        quiz.setKind(kind);
        quiz.setSubjectId("sub_ic3");
        quiz.setLevelId("lvl_ic3_gs6_l1");
        quiz.setCategoryId("top_ic3_safety");
        quiz.setStatus(quiz.getCurrentVersionId() == null ? "draft" : "published");
        quiz.setVersion(quiz.getVersion() == null ? 0 : quiz.getVersion());
        quiz.setQuestionIds(QuizDemoSeedContent.questionIds());
        quiz.setSettings(QuizDemoSeedContent.runtimeSettings(mode, shuffleQuestions, shuffleChoices, revealFeedbackPerStep));
        quiz.setResult(QuizDemoSeedContent.resultSettings());
        quiz.setTheme(QuizDemoSeedContent.theme());
        quiz.setThemeId("erg-default");
        Quiz savedQuiz = quizRepository.save(quiz);

        if (savedQuiz.getCurrentVersionId() != null) {
            return;
        }

        QuizDraft draft = new QuizDraft();
        draft.setQuizId(savedQuiz.getId());
        draft.setTenantId(TENANT_ID);
        draft.setSettings(QuizDemoSeedContent.authoringSettings(title, subtitle, mode, shuffleQuestions, shuffleChoices, revealFeedbackPerStep));
        draft.setPlayerTemplate(QuizDemoSeedContent.playerTemplate());
        draft.setTree(QuizDemoSeedContent.tree());
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setDocVersion(1L);
        draft.setUpdatedBy(SEED_USER_ID);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        savedQuiz.setLatestDraftId(savedDraft.getId());
        quizRepository.save(savedQuiz);

        quizPublishService.publish(
                TENANT_ID,
                savedQuiz.getId(),
                SEED_USER_ID,
                "seed-publish:" + savedQuiz.getId() + ":" + versionLabel,
                new PublishRequest(savedDraft.getDocVersion(), gradingMode, versionLabel)
        );
    }
}
