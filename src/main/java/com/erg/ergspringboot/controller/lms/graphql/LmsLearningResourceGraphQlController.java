package com.erg.ergspringboot.controller.lms.graphql;

import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceLibraryResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.learningcontent.LearningResourceLibraryReadService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LmsLearningResourceGraphQlController {

    private final LearningResourceLibraryReadService learningResourceLibraryReadService;
    private final GraphQlAccessSupport accessSupport;

    public LmsLearningResourceGraphQlController(
            LearningResourceLibraryReadService learningResourceLibraryReadService,
            GraphQlAccessSupport accessSupport
    ) {
        this.learningResourceLibraryReadService = learningResourceLibraryReadService;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "learningResourceLibrary")
    public LearningResourceLibraryResponse learningResourceLibrary(@Argument LearningResourceLibraryInput input) {
        LearningResourceLibraryInput safeInput = input == null ? LearningResourceLibraryInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.resource.read", "content.resource.read", "content.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        accessSupport.assertEducationUnitScope(principal, safeInput.educationUnitId());

        return learningResourceLibraryReadService.learningResourceLibrary(
                tenantId,
                safeInput.educationUnitId(),
                principal.userId(),
                accessSupport.academicYear(principal, safeInput.academicYear()),
                safeInput.subjectId(),
                safeInput.gradeId(),
                safeInput.categoryId(),
                safeInput.sectionId(),
                safeInput.topicId(),
                safeInput.status(),
                safeInput.visibility(),
                safeInput.page(),
                safeInput.size()
        );
    }

    public record LearningResourceLibraryInput(
            String tenantId,
            String educationUnitId,
            String academicYear,
            String subjectId,
            String gradeId,
            String categoryId,
            String sectionId,
            String topicId,
            String status,
            String visibility,
            Integer page,
            Integer size
    ) {

        static LearningResourceLibraryInput empty() {
            return new LearningResourceLibraryInput(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
