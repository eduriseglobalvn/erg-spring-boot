package com.erg.ergspringboot.service.curriculum;

import com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase;
import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
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
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Compatibility facade; curriculum transactions, validation and mapping are owned internally. */
@Service
public class CurriculumService {

    public static final String ACTIVE = "active";
    public static final String ARCHIVED = "archived";
    public static final String DRAFT = "draft";

    private final CurriculumTreeQueryUseCase treeQueries;
    private final CurriculumImpactQueryUseCase impactQueries;
    private final CurriculumChainValidationUseCase chainValidation;
    private final CurriculumCommandUseCase commands;

    @Autowired
    public CurriculumService(
            CurriculumTreeQueryUseCase treeQueries,
            CurriculumImpactQueryUseCase impactQueries,
            CurriculumChainValidationUseCase chainValidation,
            CurriculumCommandUseCase commands) {
        this.treeQueries = treeQueries;
        this.impactQueries = impactQueries;
        this.chainValidation = chainValidation;
        this.commands = commands;
    }

    /** Compatibility constructor retained for legacy tests and callers. */
    public CurriculumService(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            TopicContentItemRepository contentItems,
            QuestionRepository questions,
            QuizRepository quizzes,
            QuizVersionRepository quizVersions,
            AssignmentRepository assignments,
            AttemptRepository attempts) {
        CurriculumImpactQueryUseCase impactQueries = new CurriculumImpactQueryUseCase(
                subjects, levels, topics, contentItems, questions, quizzes,
                quizVersions, assignments, attempts);
        this.treeQueries = new CurriculumTreeQueryUseCase(subjects, levels, topics);
        this.impactQueries = impactQueries;
        this.chainValidation = new CurriculumChainValidationUseCase(subjects, levels, topics);
        this.commands = new CurriculumCommandUseCase(subjects, levels, topics, impactQueries);
    }

    @Cacheable(cacheNames = "curriculumTree",
            key = "{#access.tenantId(), #status == null ? 'active' : #status.toLowerCase()}")
    public CurriculumTreeResponse listTree(String status, UserAccessContext access) {
        return treeQueries.tree(status, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse createSubject(CurriculumNodeRequest request, UserAccessContext access) {
        return commands.createSubject(request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse updateSubject(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return commands.updateSubject(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        return commands.archiveSubject(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        return commands.hardDeleteSubject(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse createLevel(
            String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        return commands.createLevel(subjectId, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse updateLevel(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return commands.updateLevel(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        return commands.archiveLevel(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        return commands.hardDeleteLevel(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse createTopic(
            String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        return commands.createTopic(levelId, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse updateTopic(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return commands.updateTopic(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        return commands.archiveTopic(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        return commands.hardDeleteTopic(id, access);
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        return impactQueries.subjectImpact(id, access);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        return impactQueries.levelImpact(id, access);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        return impactQueries.topicImpact(id, access);
    }

    public CurriculumTopicResponse validateTopicChain(
            String subjectId, String levelId, String topicId, UserAccessContext access) {
        return chainValidation.validate(subjectId, levelId, topicId, access);
    }
}
