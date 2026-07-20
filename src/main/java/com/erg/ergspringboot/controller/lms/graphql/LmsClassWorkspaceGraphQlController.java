package com.erg.ergspringboot.controller.lms.graphql;

import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.lms.readmodel.LmsReadModelService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LmsClassWorkspaceGraphQlController {

    private final LmsReadModelService lmsReadModelService;
    private final GraphQlAccessSupport accessSupport;

    public LmsClassWorkspaceGraphQlController(
            LmsReadModelService lmsReadModelService,
            GraphQlAccessSupport accessSupport
    ) {
        this.lmsReadModelService = lmsReadModelService;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "classWorkspace")
    public ClassWorkspaceResponse classWorkspace(@Argument ClassWorkspaceInput input) {
        ClassWorkspaceInput safeInput = input == null ? ClassWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.class.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        accessSupport.assertClassScope(principal, safeInput.classId(), safeInput.schoolId());
        accessSupport.assertEducationUnitScope(principal, safeInput.schoolId());
        boolean includeScoreSummary = accessSupport.isAllowed(principal, "lms.grade.read", "lms.manage", "*");
        boolean includeRiskSummary = accessSupport.isAllowed(
                principal,
                "lms.progress.read",
                "lms.grade.read",
                "lms.manage",
                "*"
        );

        ClassWorkspaceResponse response = lmsReadModelService.classWorkspace(
                tenantId,
                safeInput.classId(),
                safeInput.schoolId(),
                safeInput.studentStatus(),
                safeInput.page(),
                safeInput.size(),
                safeInput.assignmentStatus(),
                safeInput.assignmentPage(),
                safeInput.assignmentSize(),
                includeScoreSummary,
                includeRiskSummary
        );
        accessSupport.assertClassOrEducationUnitResponseScope(principal, safeInput.classId(), response.classInfo().schoolId());
        return response;
    }

    public record ClassWorkspaceInput(
            String tenantId,
            @jakarta.validation.constraints.NotBlank(message = "classId is required") String classId,
            String schoolId,
            String studentStatus,
            @jakarta.validation.constraints.Min(0) Integer page,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer size,
            String assignmentStatus,
            @jakarta.validation.constraints.Min(0) Integer assignmentPage,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer assignmentSize
    ) {

        static ClassWorkspaceInput empty() {
            return new ClassWorkspaceInput(null, null, null, null, null, null, null, null, null);
        }
    }
}
