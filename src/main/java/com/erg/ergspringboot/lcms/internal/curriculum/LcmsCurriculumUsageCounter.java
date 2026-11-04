package com.erg.ergspringboot.lcms.internal.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumUsageCounter;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Questions, quizzes and published quiz versions that reference a curriculum node. */
@Component
public class LcmsCurriculumUsageCounter implements CurriculumUsageCounter {
    private final QuestionRepository questions;
    private final QuizRepository quizzes;
    private final QuizVersionRepository quizVersions;

    public LcmsCurriculumUsageCounter(QuestionRepository questions, QuizRepository quizzes,
            QuizVersionRepository quizVersions) {
        this.questions = questions;
        this.quizzes = quizzes;
        this.quizVersions = quizVersions;
    }

    @Override
    public Usage count(String tenantId, Node node, String id, List<String> topicIds) {
        return switch (node) {
            case SUBJECT -> new Usage(questions.countImpactByTenantAndSubject(tenantId, id),
                    quizzes.countImpactByTenantAndSubject(tenantId, id),
                    quizVersions.countPublishedImpactByTenantAndSubject(tenantId, id), 0, 0, 0);
            case LEVEL -> new Usage(questions.countImpactByTenantAndLevel(tenantId, id),
                    quizzes.countImpactByTenantAndLevel(tenantId, id),
                    quizVersions.countPublishedImpactByTenantAndLevel(tenantId, id), 0, 0, 0);
            case TOPIC -> new Usage(questions.countImpactByTenantAndTopic(tenantId, id),
                    quizzes.countImpactByTenantAndTopic(tenantId, id),
                    quizVersions.countPublishedImpactByTenantAndTopic(tenantId, id), 0, 0, 0);
        };
    }
}
