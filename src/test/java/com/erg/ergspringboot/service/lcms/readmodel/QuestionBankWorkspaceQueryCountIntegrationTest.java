package com.erg.ergspringboot.service.lcms.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.lms.QuizQuestion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@EnabledIf("postgresProofDatabaseAvailable")
class QuestionBankWorkspaceQueryCountIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private LcmsWorkspaceReadService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    @Qualifier("postgresEntityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    void questionBankWorkspaceKeepsQueryCountBoundedAcrossQuestionsOptionsQuizzesAndUsage() {
        assertThat(isPostgreSql())
                .as("questionBankWorkspace usage proof must run against PostgreSQL, not H2")
                .isTrue();

        String tenantId = "tenant-qbq";
        seedQuestionBank(tenantId, 36, 18);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        QuestionBankWorkspaceResponse response = service.questionBankWorkspace(
                tenantId,
                null,
                null,
                null,
                null,
                "active",
                null,
                null,
                0,
                12
        );

        assertThat(response.questions().items()).hasSize(12);
        assertThat(response.questions().totalItems()).isEqualTo(36);
        assertThat(response.subjects()).extracting("label")
                .contains("Math QBQ", "Science QBQ", "English QBQ", "History QBQ");
        assertThat(response.levels()).extracting("label")
                .contains("Grade 10 Math QBQ", "Grade 11 Science QBQ", "Grade 12 English QBQ", "Grade 9 History QBQ");
        assertThat(response.topics()).extracting("label")
                .contains("Algebra QBQ", "Physics QBQ", "Reading QBQ", "World History QBQ");
        assertThat(response.quizzes()).extracting("title").contains("Quiz QBQ 1", "Quiz QBQ 18");
        assertThat(response.questions().items())
                .anySatisfy(question -> assertThat(question.usageCount()).isGreaterThan(0L));

        long prepareStatementCount = statistics.getPrepareStatementCount();
        long entityLoadCount = statistics.getEntityLoadCount();
        assertThat(prepareStatementCount)
                .as("questionBankWorkspace should use bounded question page/count/options/quiz/usage queries")
                .isLessThanOrEqualTo(7);
        assertThat(entityLoadCount)
                .as("projection read should not materialize question/quiz/taxonomy graphs")
                .isZero();

        List<String> firstPageQuestionIds = response.questions().items().stream()
                .map(com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankQuestionRowResponse::id)
                .toList();
        List<String> explainPlan = explainUsageQuery(tenantId, firstPageQuestionIds);
        assertThat(explainPlan).isNotEmpty();
        assertThat(String.join("\n", explainPlan))
                .contains("idx_lms_quiz_questions_question")
                .doesNotContain("jsonb_array_elements_text")
                .contains("Execution Time");
    }

    private void seedQuestionBank(String tenantId, int questionCount, int quizCount) {
        List<TaxonomyRef> taxonomy = List.of(
                taxonomy(tenantId, "math", "Math QBQ", "Grade 10 Math QBQ", "Algebra QBQ", 1),
                taxonomy(tenantId, "science", "Science QBQ", "Grade 11 Science QBQ", "Physics QBQ", 2),
                taxonomy(tenantId, "english", "English QBQ", "Grade 12 English QBQ", "Reading QBQ", 3),
                taxonomy(tenantId, "history", "History QBQ", "Grade 9 History QBQ", "World History QBQ", 4)
        );

        for (int index = 1; index <= questionCount; index++) {
            TaxonomyRef ref = taxonomy.get((index - 1) % taxonomy.size());
            Question question = new Question();
            question.setId(questionId(index));
            question.setTenantId(tenantId);
            question.setSubjectId(ref.subject().getId());
            question.setLevelId(ref.level().getId());
            question.setTopicId(ref.topic().getId());
            question.setType("multiple_choice");
            question.setStem("Question QBQ " + index);
            question.setStatus("active");
            entityManager.persist(question);
        }

        for (int index = 1; index <= quizCount; index++) {
            TaxonomyRef ref = taxonomy.get((index - 1) % taxonomy.size());
            Quiz quiz = new Quiz();
            quiz.setId(quizId(index));
            quiz.setTenantId(tenantId);
            quiz.setTitle("Quiz QBQ " + index);
            quiz.setKind("practice");
            quiz.setSubjectId(ref.subject().getId());
            quiz.setLevelId(ref.level().getId());
            quiz.setTopicIds(List.of(ref.topic().getId()));
            quiz.setQuestionIds(List.of(
                    questionId(index),
                    questionId(wrapQuestionIndex(index + quizCount, questionCount)),
                    questionId(wrapQuestionIndex(index + quizCount + 1, questionCount)),
                    questionId(wrapQuestionIndex(index + quizCount + 2, questionCount)),
                    questionId(wrapQuestionIndex(index + quizCount + 3, questionCount))
            ));
            quiz.setStatus("active");
            quiz.setVersion(1);
            entityManager.persist(quiz);
        }
        entityManager.flush();

        for (int quizIndex = 1; quizIndex <= quizCount; quizIndex++) {
            List<String> linkedQuestionIds = List.of(
                    questionId(quizIndex),
                    questionId(wrapQuestionIndex(quizIndex + quizCount, questionCount)),
                    questionId(wrapQuestionIndex(quizIndex + quizCount + 1, questionCount)),
                    questionId(wrapQuestionIndex(quizIndex + quizCount + 2, questionCount)),
                    questionId(wrapQuestionIndex(quizIndex + quizCount + 3, questionCount))
            );
            for (int orderIndex = 0; orderIndex < linkedQuestionIds.size(); orderIndex++) {
                QuizQuestion link = new QuizQuestion();
                link.setTenantId(tenantId);
                link.setQuizId(quizId(quizIndex));
                link.setQuestionId(linkedQuestionIds.get(orderIndex));
                link.setOrderIndex(orderIndex);
                link.setSource("bank");
                entityManager.persist(link);
            }
        }
    }

    private List<String> explainUsageQuery(String tenantId, List<String> questionIds) {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            List<String> plan = new ArrayList<>();
            Array questionIdArray = connection.createArrayOf("text", questionIds.toArray(String[]::new));
            try (PreparedStatement statement = connection.prepareStatement("""
                    explain (analyze, buffers, format text)
                    select link.question_id as questionId, count(distinct link.quiz_id) as usageCount
                    from lms_quiz_questions link
                    join lms_quizzes quiz on quiz.id = link.quiz_id and quiz.tenant_id = link.tenant_id
                    where link.tenant_id = ?
                    and link.question_id = any (?)
                    group by link.question_id
                    """)) {
                statement.setString(1, tenantId);
                statement.setArray(2, questionIdArray);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        plan.add(resultSet.getString(1));
                    }
                }
            } finally {
                questionIdArray.free();
            }
            return plan;
        });
    }

    private boolean isPostgreSql() {
        return entityManager.unwrap(Session.class).doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")
        );
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

    private TaxonomyRef taxonomy(
            String tenantId,
            String slug,
            String subjectName,
            String levelName,
            String topicName,
            int order
    ) {
        CurriculumSubject subject = subject("sub-qbq-" + slug, tenantId, subjectName, slug + "-qbq");
        CurriculumLevel level = level("lvl-qbq-" + slug, tenantId, subject.getId(), levelName, order);
        CurriculumTopic topic = topic("top-qbq-" + slug, tenantId, level.getId(), topicName, order);
        return new TaxonomyRef(subject, level, topic);
    }

    private String questionId(int index) {
        return "qbq-q-" + index;
    }

    private String quizId(int index) {
        return "qbq-quiz-" + index;
    }

    private int wrapQuestionIndex(int index, int questionCount) {
        return ((index - 1) % questionCount) + 1;
    }

    private CurriculumSubject subject(String id, String tenantId, String name, String code) {
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId(id);
        subject.setTenantId(tenantId);
        subject.setName(name);
        subject.setCode(code);
        subject.setStatus("active");
        subject.setSortOrder(0);
        entityManager.persist(subject);
        return subject;
    }

    private CurriculumLevel level(String id, String tenantId, String subjectId, String name, int order) {
        CurriculumLevel level = new CurriculumLevel();
        level.setId(id);
        level.setTenantId(tenantId);
        level.setSubjectId(subjectId);
        level.setName(name);
        level.setCode(id);
        level.setSortOrder(order);
        level.setStatus("active");
        entityManager.persist(level);
        return level;
    }

    private CurriculumTopic topic(String id, String tenantId, String levelId, String name, int order) {
        CurriculumTopic topic = new CurriculumTopic();
        topic.setId(id);
        topic.setTenantId(tenantId);
        topic.setLevelId(levelId);
        topic.setName(name);
        topic.setCode(id);
        topic.setSortOrder(order);
        topic.setStatus("active");
        entityManager.persist(topic);
        return topic;
    }

    private record TaxonomyRef(CurriculumSubject subject, CurriculumLevel level, CurriculumTopic topic) {
    }
}

