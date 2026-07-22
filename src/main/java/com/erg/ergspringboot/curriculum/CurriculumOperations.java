package com.erg.ergspringboot.curriculum;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumService;
import org.springframework.stereotype.Service;

/** Public module facade for curriculum taxonomy queries, validation and lifecycle commands. */
@Service
public class CurriculumOperations {

    private final CurriculumService compatibility;

    public CurriculumOperations(CurriculumService compatibility) {
        this.compatibility = compatibility;
    }

    public CurriculumTreeResponse listTree(String status, UserAccessContext access) {
        return compatibility.listTree(status, access);
    }

    public CurriculumSubjectResponse createSubject(
            CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.createSubject(request, access);
    }

    public CurriculumSubjectResponse updateSubject(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.updateSubject(id, request, access);
    }

    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        return compatibility.archiveSubject(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        return compatibility.hardDeleteSubject(id, access);
    }

    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        return compatibility.subjectImpact(id, access);
    }

    public CurriculumLevelResponse createLevel(
            String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.createLevel(subjectId, request, access);
    }

    public CurriculumLevelResponse updateLevel(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.updateLevel(id, request, access);
    }

    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        return compatibility.archiveLevel(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        return compatibility.hardDeleteLevel(id, access);
    }

    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        return compatibility.levelImpact(id, access);
    }

    public CurriculumTopicResponse createTopic(
            String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.createTopic(levelId, request, access);
    }

    public CurriculumTopicResponse updateTopic(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        return compatibility.updateTopic(id, request, access);
    }

    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        return compatibility.archiveTopic(id, access);
    }

    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        return compatibility.hardDeleteTopic(id, access);
    }

    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        return compatibility.topicImpact(id, access);
    }

    public CurriculumTopicResponse validateTopicChain(
            String subjectId, String levelId, String topicId, UserAccessContext access) {
        return compatibility.validateTopicChain(subjectId, levelId, topicId, access);
    }
}
