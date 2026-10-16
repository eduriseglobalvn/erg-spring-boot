package com.erg.ergspringboot.service.curriculum;

import com.erg.ergspringboot.curriculum.CurriculumOperations;
import com.erg.ergspringboot.curriculum.CurriculumOperationsCompatibility;
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
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link CurriculumOperations}. This bean is a compatibility adapter only;
 * curriculum use cases are owned by the explicit module.
 */
@Deprecated
@Service
public class CurriculumService {

    public static final String ACTIVE = "active";
    public static final String ARCHIVED = "archived";
    public static final String DRAFT = "draft";

    private final CurriculumOperations operations;

    @Autowired
    public CurriculumService(CurriculumOperations operations) {
        this.operations = operations;
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
        this(CurriculumOperationsCompatibility.fromRepositories(
                subjects, levels, topics, contentItems, questions, quizzes,
                quizVersions, assignments, attempts));
    }

    public CurriculumTreeResponse listTree(String status, UserAccessContext access) {
        return operations.listTree(status, access);
    }

    public CurriculumSubjectResponse createSubject(CurriculumNodeRequest request, UserAccessContext access) {
        return operations.createSubject(request, access);
    }

    public CurriculumSubjectResponse updateSubject(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return operations.updateSubject(id, request, access);
    }

    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        return operations.archiveSubject(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        return operations.hardDeleteSubject(id, access);
    }

    public CurriculumLevelResponse createLevel(
            String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        return operations.createLevel(subjectId, request, access);
    }

    public CurriculumLevelResponse updateLevel(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return operations.updateLevel(id, request, access);
    }

    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        return operations.archiveLevel(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        return operations.hardDeleteLevel(id, access);
    }

    public CurriculumTopicResponse createTopic(
            String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        return operations.createTopic(levelId, request, access);
    }

    public CurriculumTopicResponse updateTopic(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return operations.updateTopic(id, request, access);
    }

    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        return operations.archiveTopic(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        return operations.hardDeleteTopic(id, access);
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        return operations.subjectImpact(id, access);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        return operations.levelImpact(id, access);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        return operations.topicImpact(id, access);
    }

    public CurriculumTopicResponse validateTopicChain(
            String subjectId, String levelId, String topicId, UserAccessContext access) {
        return operations.validateTopicChain(subjectId, levelId, topicId, access);
    }
}
