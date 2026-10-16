package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase;
import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Public module facade for curriculum taxonomy queries, validation and lifecycle commands. */
@Service
public class CurriculumOperations {

    private final CurriculumTreeQueryUseCase treeQueries;
    private final CurriculumImpactQueryUseCase impactQueries;
    private final CurriculumChainValidationUseCase chainValidation;
    private final CurriculumCommandUseCase commands;
    private final CurriculumService compatibility;

    @Autowired
    public CurriculumOperations(
            CurriculumTreeQueryUseCase treeQueries,
            CurriculumImpactQueryUseCase impactQueries,
            CurriculumChainValidationUseCase chainValidation,
            CurriculumCommandUseCase commands) {
        this.treeQueries = treeQueries;
        this.impactQueries = impactQueries;
        this.chainValidation = chainValidation;
        this.commands = commands;
        this.compatibility = null;
    }

    /** Compatibility constructor for focused adapter tests during strangler migration. */
    @Deprecated
    public CurriculumOperations(CurriculumService compatibility) {
        this.treeQueries = null;
        this.impactQueries = null;
        this.chainValidation = null;
        this.commands = null;
        this.compatibility = compatibility;
    }

    @Cacheable(cacheNames = "curriculumTree",
            key = "{#access.tenantId(), #status == null ? 'active' : #status.toLowerCase()}")
    public CurriculumTreeResponse listTree(String status, UserAccessContext access) {
        return compatibility != null ? compatibility.listTree(status, access) : treeQueries.tree(status, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse createSubject(
            CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.createSubject(request, access) : commands.createSubject(request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse updateSubject(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.updateSubject(id, request, access) : commands.updateSubject(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.archiveSubject(id, access) : commands.archiveSubject(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.hardDeleteSubject(id, access) : commands.hardDeleteSubject(id, access);
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        return compatibility != null ? compatibility.subjectImpact(id, access) : impactQueries.subjectImpact(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse createLevel(
            String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.createLevel(subjectId, request, access) : commands.createLevel(subjectId, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse updateLevel(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.updateLevel(id, request, access) : commands.updateLevel(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.archiveLevel(id, access) : commands.archiveLevel(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.hardDeleteLevel(id, access) : commands.hardDeleteLevel(id, access);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        return compatibility != null ? compatibility.levelImpact(id, access) : impactQueries.levelImpact(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse createTopic(
            String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.createTopic(levelId, request, access) : commands.createTopic(levelId, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse updateTopic(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility != null
                ? compatibility.updateTopic(id, request, access) : commands.updateTopic(id, request, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.archiveTopic(id, access) : commands.archiveTopic(id, access);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        return compatibility != null
                ? compatibility.hardDeleteTopic(id, access) : commands.hardDeleteTopic(id, access);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        return compatibility != null ? compatibility.topicImpact(id, access) : impactQueries.topicImpact(id, access);
    }

    public CurriculumTopicResponse validateTopicChain(
            String subjectId, String levelId, String topicId, UserAccessContext access) {
        return compatibility != null
                ? compatibility.validateTopicChain(subjectId, levelId, topicId, access)
                : chainValidation.validate(subjectId, levelId, topicId, access);
    }
}
