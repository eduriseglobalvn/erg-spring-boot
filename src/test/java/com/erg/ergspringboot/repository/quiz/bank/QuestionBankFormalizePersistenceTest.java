package com.erg.ergspringboot.repository.quiz.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.quiz.bank.QuestionBankCategory;
import com.erg.ergspringboot.model.quiz.bank.QuestionBankLevel;
import com.erg.ergspringboot.model.quiz.bank.QuestionBankSubject;
import com.erg.ergspringboot.model.quiz.bank.QuizBankItem;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
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
 * Phase 0.3 proof: booting the app validates V24 against the entities (hbm2ddl.auto=validate), and
 * the enriched lms_questions columns plus new bank taxonomy tables round-trip on real PostgreSQL.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class QuestionBankFormalizePersistenceTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionBankSubjectRepository subjectRepository;

    @Autowired
    private QuestionBankLevelRepository levelRepository;

    @Autowired
    private QuestionBankCategoryRepository categoryRepository;

    @Autowired
    private QuizBankItemRepository quizBankItemRepository;

    @Test
    void enrichedQuestionAndBankTaxonomyRoundTrip() {
        String tenantId = "tenant-bank-0-3";

        QuestionBankSubject subject = new QuestionBankSubject();
        subject.setTenantId(tenantId);
        subject.setLabel("Toán");
        subject.setCompanyScopeLabel("ERG");
        QuestionBankSubject savedSubject = subjectRepository.save(subject);

        QuestionBankLevel level = new QuestionBankLevel();
        level.setTenantId(tenantId);
        level.setSubjectId(savedSubject.getId());
        level.setLabel("Khối 6");
        level.setOrderIndex(1);
        QuestionBankLevel savedLevel = levelRepository.save(level);

        QuestionBankCategory category = new QuestionBankCategory();
        category.setTenantId(tenantId);
        category.setSubjectId(savedSubject.getId());
        category.setLevelId(savedLevel.getId());
        category.setLabel("Số học");
        QuestionBankCategory savedCategory = categoryRepository.save(category);

        Question question = new Question();
        question.setTenantId(tenantId);
        question.setSubjectId("sub_ic3");
        question.setLevelId("lvl_ic3_gs6_l1");
        question.setTopicId("top_ic3_safety");
        question.setCategoryId("top_ic3_safety");
        question.setType("multiple_choice");
        question.setStem("2 + 2 = ?");
        question.setObjective("Phép cộng cơ bản");
        question.setDifficulty("core");
        question.setTags(List.of("arithmetic", "addition"));
        question.setContent(Map.of("body", Map.of("choices", List.of("3", "4")), "key", "4"));
        question.setMasteryRate(87.5);
        question.setUsageCount(3);
        question.setSchoolsUsing(2);
        question.setSchemaVersion(1);
        question.setStatus("ready");
        Question savedQuestion = questionRepository.save(question);

        QuizBankItem item = new QuizBankItem();
        item.setTenantId(tenantId);
        item.setTitle("Đề luyện số học");
        item.setKind("train");
        item.setStatus("ready");
        item.setSubjectId(savedSubject.getId());
        item.setLevelId(savedLevel.getId());
        item.setQuestionIds(List.of(savedQuestion.getId()));
        item.setSourceMode("manual");
        item.setDurationLabel("15 phút");
        quizBankItemRepository.save(item);

        Question reloaded = questionRepository.findById(savedQuestion.getId()).orElseThrow();
        assertThat(reloaded.getObjective()).isEqualTo("Phép cộng cơ bản");
        assertThat(reloaded.getDifficulty()).isEqualTo("core");
        assertThat(reloaded.getTags()).containsExactly("arithmetic", "addition");
        assertThat(reloaded.getContent()).containsEntry("key", "4");
        assertThat(reloaded.getMasteryRate()).isEqualTo(87.5);
        assertThat(reloaded.getUsageCount()).isEqualTo(3);

        assertThat(quizBankItemRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getQuestionIds()).containsExactly(savedQuestion.getId());
                    assertThat(saved.getKind()).isEqualTo("train");
                });
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
