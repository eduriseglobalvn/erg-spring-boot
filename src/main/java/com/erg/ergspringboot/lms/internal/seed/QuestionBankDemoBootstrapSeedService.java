package com.erg.ergspringboot.lms.internal.seed;

import com.erg.ergspringboot.lms.persistence.Level;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Question;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionChoice;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.lms.persistence.Topic;
import com.erg.ergspringboot.lms.persistence.LevelRepository;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import com.erg.ergspringboot.lms.persistence.TopicRepository;
import com.erg.ergspringboot.lcms.quiz.authoring.QuizQuestionLinkService;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.LevelSeed;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.QuestionSeed;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.QuizBankItemSeed;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.SubjectSeed;
import com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.TopicSeed;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.erg.ergspringboot.lms.internal.seed.QuestionBankDemoSeedContent.map;

/** Upserts the {@link QuestionBankDemoSeedContent} catalogue into the question bank. */
@Service
public class QuestionBankDemoBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String ACTIVE = "ACTIVE";
    private static final String SEED_USER_ID = "seed.question.bank";

    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final QuizDraftRepository quizDraftRepository;
    private final QuizQuestionLinkService quizQuestionLinkService;

    public QuestionBankDemoBootstrapSeedService(
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            TopicRepository topicRepository,
            QuestionRepository questionRepository,
            QuizRepository quizRepository,
            QuizDraftRepository quizDraftRepository,
            QuizQuestionLinkService quizQuestionLinkService
    ) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.quizDraftRepository = quizDraftRepository;
        this.quizQuestionLinkService = quizQuestionLinkService;
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    public void seedQuestionBankDemo() {
        seedSubjects();
        seedQuestions();
        seedQuizBankItems();
    }

    private void seedSubjects() {
        QuestionBankDemoSeedContent.SUBJECTS.forEach(this::upsertSubject);
        deactivateLegacyDemoSubjects();
        QuestionBankDemoSeedContent.LEVELS.forEach(this::upsertLevel);
        QuestionBankDemoSeedContent.TOPICS.forEach(this::seedTopics);
    }

    private void seedTopics(TopicSeed seed) {
        String levelId = seed.levelId();
        List<String> labels = seed.labels();
        for (int index = 0; index < labels.size(); index += 1) {
            upsertTopic(levelId + "-topic-" + (index + 1), levelId, labels.get(index), levelId + "-topic-" + (index + 1), index + 1);
        }
    }

    private void deactivateLegacyDemoSubjects() {
        QuestionBankDemoSeedContent.LEGACY_SUBJECT_IDS.forEach(subjectId -> subjectRepository.findById(subjectId).ifPresent(subject -> {
            subject.setStatus("INACTIVE");
            subjectRepository.save(subject);
        }));
    }

    private void seedQuestions() {
        QuestionBankDemoSeedContent.QUESTIONS.forEach(this::upsertQuestion);
    }

    private void seedQuizBankItems() {
        QuestionBankDemoSeedContent.QUIZ_BANK_ITEMS.forEach(this::upsertQuizBankItem);
        upsertSampleEditorDraft(QuestionBankDemoSeedContent.SAMPLE_EDITOR_QUIZ_ID);
    }

    private void upsertSubject(SubjectSeed seed) {
        String id = seed.id();
        Subject subject = subjectRepository.findById(id).orElseGet(() -> {
            Subject created = new Subject();
            created.setId(id);
            return created;
        });
        subject.setTenantId(TENANT_ID);
        subject.setName(seed.name());
        subject.setCode(seed.code());
        subject.setStatus(ACTIVE);
        subjectRepository.save(subject);
    }

    private void upsertLevel(LevelSeed seed) {
        String id = seed.id();
        Level level = levelRepository.findById(id).orElseGet(() -> {
            Level created = new Level();
            created.setId(id);
            return created;
        });
        level.setTenantId(TENANT_ID);
        level.setSubjectId(seed.subjectId());
        level.setName(seed.name());
        level.setCode(seed.code());
        level.setOrder(seed.order());
        level.setStatus(ACTIVE);
        levelRepository.save(level);
    }

    private void upsertTopic(String id, String levelId, String name, String code, int order) {
        Topic topic = topicRepository.findById(id).orElseGet(() -> {
            Topic created = new Topic();
            created.setId(id);
            return created;
        });
        topic.setTenantId(TENANT_ID);
        topic.setLevelId(levelId);
        topic.setName(name);
        topic.setCode(code);
        topic.setOrder(order);
        topic.setStatus(ACTIVE);
        topicRepository.save(topic);
    }

    private void upsertQuestion(QuestionSeed seed) {
        String id = seed.id();
        String subjectId = seed.subjectId();
        String levelId = seed.levelId();
        String topicId = seed.topicId();
        String stem = seed.stem();
        String answer = seed.answer();
        Question question = questionRepository.findById(id).orElseGet(() -> {
            Question created = new Question();
            created.setId(id);
            return created;
        });
        question.setTenantId(TENANT_ID);
        question.setSubjectId(canonicalSubjectId(subjectId));
        question.setLevelId(canonicalLevelId(levelId));
        question.setTopicId(canonicalTopicId(topicId));
        question.setCategoryId(canonicalTopicId(topicId));
        question.setType(normalizeQuestionType(seed.type()));
        question.setStem(stem);
        question.setObjective(stem);
        question.setDifficulty(seed.difficulty());
        question.setStatus(seed.status());
        question.setTags(List.of(subjectId, levelId, topicId));
        question.setChoices(defaultChoices(answer));
        question.setAnswer(answer);
        question.setContent(map("stem", stem, "answer", answer, "source", "fe-mock-question-bank"));
        question.setMetadata(map("seededFrom", "erg-system mock-question-bank.ts"));
        question.setMasteryRate(seed.masteryRate());
        question.setUsageCount(seed.usageCount());
        question.setSchoolsUsing(seed.schoolsUsing());
        question.setOwnerUserId(SEED_USER_ID);
        question.setSchemaVersion(1);
        questionRepository.save(question);
    }

    private void upsertQuizBankItem(QuizBankItemSeed seed) {
        String id = seed.id();
        List<String> questionIds = seed.questionIds();
        Quiz quiz = quizRepository.findById(id).orElseGet(() -> {
            Quiz created = new Quiz();
            created.setId(id);
            return created;
        });
        quiz.setTenantId(TENANT_ID);
        quiz.setPortal("lcms");
        quiz.setTitle(seed.title());
        quiz.setKind(seed.kind());
        quiz.setSubjectId(canonicalSubjectId(seed.subjectId()));
        quiz.setLevelId(canonicalLevelId(seed.levelId()));
        quiz.setCategoryId(questionIds.isEmpty() ? null : canonicalTopicId(topicIdForQuestion(questionIds.get(0))));
        quiz.setQuestionIds(questionIds);
        quiz.setStatus(seed.status());
        quiz.setVersion(quiz.getVersion() == null ? 0 : quiz.getVersion());
        quiz.setOwnerUserId(SEED_USER_ID);
        quiz.setSchemaVersion(1);
        quiz.setSettings(map("sourceMode", "fe-mock-question-bank"));
        Quiz savedQuiz = quizRepository.save(quiz);
        savedQuiz.setQuestionIds(quizQuestionLinkService.syncManualQuestionIds(TENANT_ID, savedQuiz.getId(), questionIds));
        quizRepository.save(savedQuiz);
    }

    private void upsertSampleEditorDraft(String quizId) {
        QuizDraft draft = quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(quizId, TENANT_ID)
                .orElseGet(() -> {
                    QuizDraft created = new QuizDraft();
                    created.setQuizId(quizId);
                    created.setTenantId(TENANT_ID);
                    return created;
                });
        draft.setSettings(QuestionBankDemoSeedContent.sampleEditorDraftSettings());
        draft.setPlayerTemplate(QuestionBankDemoSeedContent.sampleEditorPlayerTemplate());
        draft.setTree(QuestionBankDemoSeedContent.sampleEditorTree());
        draft.setSchemaVersion(1);
        draft.setDocVersion(draft.getDocVersion() == null ? 1L : draft.getDocVersion() + 1L);
        draft.setUpdatedBy(SEED_USER_ID);
        QuizDraft savedDraft = quizDraftRepository.save(draft);

        quizRepository.findByIdAndTenantId(quizId, TENANT_ID).ifPresent(quiz -> {
            quiz.setLatestDraftId(savedDraft.getId());
            quizRepository.save(quiz);
        });
    }

    private String topicIdForQuestion(String questionId) {
        return questionRepository.findById(questionId).map(Question::getTopicId).orElse(null);
    }

    private String canonicalSubjectId(String id) {
        return switch (id) {
            case "ic3-gs6" -> "sub_ic3";
            case "python" -> "sub_python";
            case "mos" -> "sub_mos";
            default -> id;
        };
    }

    private String canonicalLevelId(String id) {
        if (id != null && id.startsWith("ic3-l")) return "lvl_ic3_gs6_l1";
        return switch (id) {
            case "python-foundation" -> "lvl_python_foundation";
            case "python-intermediate" -> "lvl_python_intermediate";
            case "mos-word" -> "lvl_mos_word";
            case "mos-excel" -> "lvl_mos_excel";
            case "mos-powerpoint" -> "lvl_mos_powerpoint";
            default -> id;
        };
    }

    private String canonicalTopicId(String id) {
        if (id != null && id.startsWith("ic3-")) return "top_ic3_safety";
        if (id != null && id.startsWith("python-foundation")) return "top_python_foundation";
        if (id != null && id.startsWith("python-intermediate")) return "top_python_intermediate";
        if (id != null && id.startsWith("mos-word")) return "top_mos_word";
        if (id != null && id.startsWith("mos-excel")) return "top_mos_excel";
        if (id != null && id.startsWith("mos-powerpoint")) return "top_mos_powerpoint";
        return id;
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return "multiple_choice";
        }
        return type.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace("fill_in_the_blanks", "fill_blank")
                .replace("drag_the_words", "drag_words")
                .replace("drag_and_drop", "drag_drop");
    }

    private List<QuestionChoice> defaultChoices(String answer) {
        return List.of(
                new QuestionChoice("a", answer, true),
                new QuestionChoice("b", "Phuong an gay nhieu 1", false),
                new QuestionChoice("c", "Phuong an gay nhieu 2", false),
                new QuestionChoice("d", "Phuong an gay nhieu 3", false)
        );
    }
}
