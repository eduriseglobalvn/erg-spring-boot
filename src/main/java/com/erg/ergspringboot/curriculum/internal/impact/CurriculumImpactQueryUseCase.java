package com.erg.ergspringboot.curriculum.internal.impact;

import com.erg.ergspringboot.curriculum.persistence.CurriculumLevel;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubject;
import com.erg.ergspringboot.curriculum.persistence.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.curriculum.persistence.CurriculumLevelRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumTopicRepository;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumImpactCounts;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.curriculum.CurriculumException;
import com.erg.ergspringboot.curriculum.CurriculumUsageCounter;
import com.erg.ergspringboot.curriculum.CurriculumUsageCounter.Node;
import com.erg.ergspringboot.curriculum.CurriculumUsageCounter.Usage;
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
    private final List<CurriculumUsageCounter> usageCounters;

    public CurriculumImpactQueryUseCase(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            List<CurriculumUsageCounter> usageCounters) {
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
        if (usageCounters.isEmpty()) {
            // Fail closed: with no counters every node would look unused and become hard-deletable.
            throw new IllegalStateException("no CurriculumUsageCounter beans registered");
        }
        this.usageCounters = List.copyOf(usageCounters);
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(id, tenantId);
        long childLevels = levels.countByTenantIdAndSubjectId(tenantId, id);
        List<String> topicIds = topicIdsForSubject(tenantId, id);
        Usage usage = usage(tenantId, Node.SUBJECT, id, topicIds);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                usage.questions(),
                usage.quizzes(),
                usage.publishedQuizVersions(),
                usage.contentItems(),
                usage.assignments(),
                usage.studentAttempts(),
                childLevels,
                topicIds.size());
        return impact(subject.getId(), "subject", subject.getStatus(), counts);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumLevel level = requireLevel(id, tenantId);
        long childTopics = topics.countByTenantIdAndLevelId(tenantId, id);
        List<String> topicIds = topicIdsForLevel(tenantId, id);
        Usage usage = usage(tenantId, Node.LEVEL, id, topicIds);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                usage.questions(),
                usage.quizzes(),
                usage.publishedQuizVersions(),
                usage.contentItems(),
                usage.assignments(),
                usage.studentAttempts(),
                0,
                childTopics);
        return impact(level.getId(), "level", level.getStatus(), counts);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        Usage usage = usage(tenantId, Node.TOPIC, id, List.of(id));
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                usage.questions(),
                usage.quizzes(),
                usage.publishedQuizVersions(),
                usage.contentItems(),
                usage.assignments(),
                usage.studentAttempts(),
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

    private Usage usage(String tenantId, Node node, String id, List<String> topicIds) {
        Usage total = Usage.NONE;
        for (CurriculumUsageCounter counter : usageCounters) {
            total = total.plus(counter.count(tenantId, node, id, topicIds));
        }
        return total;
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
