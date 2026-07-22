package com.erg.ergspringboot.curriculum.internal.impact;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactCounts;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns dependency-impact reads used by preview and hard-delete safety checks. */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class CurriculumImpactQueryUseCase {

    private static final String ARCHIVED = "archived";

    private final CurriculumSubjectRepository subjects;
    private final CurriculumLevelRepository levels;
    private final CurriculumTopicRepository topics;
    private final TopicContentItemRepository contentItems;
    private final QuestionRepository questions;
    private final QuizRepository quizzes;
    private final QuizVersionRepository quizVersions;
    private final AssignmentRepository assignments;
    private final AttemptRepository attempts;

    public CurriculumImpactQueryUseCase(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            TopicContentItemRepository contentItems,
            QuestionRepository questions,
            QuizRepository quizzes,
            QuizVersionRepository quizVersions,
            AssignmentRepository assignments,
            AttemptRepository attempts) {
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
        this.contentItems = contentItems;
        this.questions = questions;
        this.quizzes = quizzes;
        this.quizVersions = quizVersions;
        this.assignments = assignments;
        this.attempts = attempts;
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(id, tenantId);
        long childLevels = levels.countByTenantIdAndSubjectId(tenantId, id);
        List<String> topicIds = topicIdsForSubject(tenantId, id);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questions.countImpactByTenantAndSubject(tenantId, id),
                quizzes.countImpactByTenantAndSubject(tenantId, id),
                quizVersions.countPublishedImpactByTenantAndSubject(tenantId, id),
                contentItemCount(tenantId, topicIds),
                assignments.countImpactByTenantAndSubject(tenantId, id),
                attempts.countImpactByTenantAndSubject(tenantId, id),
                childLevels,
                topicIds.size());
        return impact(subject.getId(), "subject", subject.getStatus(), counts);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumLevel level = requireLevel(id, tenantId);
        long childTopics = topics.countByTenantIdAndLevelId(tenantId, id);
        List<String> topicIds = topicIdsForLevel(tenantId, id);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questions.countImpactByTenantAndLevel(tenantId, id),
                quizzes.countImpactByTenantAndLevel(tenantId, id),
                quizVersions.countPublishedImpactByTenantAndLevel(tenantId, id),
                contentItemCount(tenantId, topicIds),
                assignments.countImpactByTenantAndLevel(tenantId, id),
                attempts.countImpactByTenantAndLevel(tenantId, id),
                0,
                childTopics);
        return impact(level.getId(), "level", level.getStatus(), counts);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questions.countImpactByTenantAndTopic(tenantId, id),
                quizzes.countImpactByTenantAndTopic(tenantId, id),
                quizVersions.countPublishedImpactByTenantAndTopic(tenantId, id),
                contentItems.countByTenantIdAndTopicId(tenantId, id),
                assignments.countImpactByTenantAndTopic(tenantId, id),
                attempts.countImpactByTenantAndTopic(tenantId, id),
                0,
                0);
        return impact(topic.getId(), "topic", topic.getStatus(), counts);
    }

    private CurriculumImpactResponse impact(
            String id, String nodeType, String status, CurriculumImpactCounts counts) {
        boolean noDependencies = counts.questions() == 0
                && counts.quizzes() == 0
                && counts.publishedQuizVersions() == 0
                && counts.contentItems() == 0
                && counts.assignments() == 0
                && counts.studentAttempts() == 0
                && counts.childLevels() == 0
                && counts.childTopics() == 0;
        List<String> recommended = new ArrayList<>();
        if (noDependencies) {
            recommended.add("HARD_DELETE");
        }
        if (!ARCHIVED.equalsIgnoreCase(status)) {
            recommended.add("ARCHIVE");
        }
        recommended.add("REASSIGN");
        recommended.add("MERGE");
        return new CurriculumImpactResponse(
                id,
                nodeType,
                noDependencies,
                !ARCHIVED.equalsIgnoreCase(status),
                counts,
                recommended,
                "Impact counts include curriculum children, topic content placements, LMS questions/quizzes, published quiz versions, assignments, and attempts from existing relationships.");
    }

    private List<String> topicIdsForSubject(String tenantId, String subjectId) {
        List<CurriculumLevel> childLevels = levels.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(
                tenantId, List.of(subjectId));
        return topicIdsForLevels(tenantId, childLevels.stream().map(CurriculumLevel::getId).toList());
    }

    private List<String> topicIdsForLevel(String tenantId, String levelId) {
        return topicIdsForLevels(tenantId, List.of(levelId));
    }

    private List<String> topicIdsForLevels(String tenantId, List<String> levelIds) {
        if (levelIds.isEmpty()) {
            return List.of();
        }
        return topics.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(tenantId, levelIds)
                .stream().map(CurriculumTopic::getId).toList();
    }

    private long contentItemCount(String tenantId, List<String> topicIds) {
        return topicIds.isEmpty() ? 0 : contentItems.countByTenantIdAndTopicIdIn(tenantId, topicIds);
    }

    private CurriculumSubject requireSubject(String id, String tenantId) {
        return subjects.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("subject", id));
    }

    private CurriculumLevel requireLevel(String id, String tenantId) {
        return levels.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("level", id));
    }

    private CurriculumTopic requireTopic(String id, String tenantId) {
        return topics.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("topic", id));
    }

    private String tenantId(UserAccessContext access) {
        return access == null || blankToNull(access.tenantId()) == null ? "erg" : access.tenantId().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
