package com.erg.ergspringboot.controller.lcms.graphql;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.lcms.readmodel.LcmsWorkspaceReadService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LcmsWorkspaceGraphQlController {

    private final GraphQlAccessSupport accessSupport;
    private final LcmsWorkspaceReadService readService;

    public LcmsWorkspaceGraphQlController(
            GraphQlAccessSupport accessSupport,
            LcmsWorkspaceReadService readService
    ) {
        this.accessSupport = accessSupport;
        this.readService = readService;
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "questionBankWorkspace")
    public QuestionBankWorkspaceResponse questionBankWorkspace(@Argument QuestionBankWorkspaceInput input) {
        QuestionBankWorkspaceInput safeInput = input == null ? QuestionBankWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.exercise.read", "lms.quiz.read", "content.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        return readService.questionBankWorkspace(
                tenantId,
                safeInput.search(),
                safeInput.subjectId(),
                safeInput.levelId(),
                safeInput.topicId(),
                safeInput.status(),
                safeInput.quizKind(),
                safeInput.includeQuestions(),
                safeInput.page(),
                safeInput.size()
        );
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "accessManagementWorkspace")
    public AccessManagementWorkspaceResponse accessManagementWorkspace(@Argument AccessManagementWorkspaceInput input) {
        AccessManagementWorkspaceInput safeInput = input == null ? AccessManagementWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.scope.read", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        return readService.accessManagementWorkspace(
                tenantId,
                safeInput.search(),
                safeInput.roleId(),
                safeInput.status(),
                safeInput.page(),
                safeInput.size(),
                canReadEffectivePermissions(principal)
        );
    }

    private boolean canReadEffectivePermissions(ErgPrincipal principal) {
        if (principal == null) {
            return false;
        }
        List<String> deniedPermissions = principal.deniedPermissions() == null ? List.of() : principal.deniedPermissions();
        if (deniedPermissions.contains("*")
                || deniedPermissions.contains("rbac.permission.read")
                || deniedPermissions.contains("users.manage")) {
            return false;
        }
        List<String> permissions = principal.permissions() == null ? List.of() : principal.permissions();
        return permissions.contains("*")
                || permissions.contains("rbac.permission.read")
                || permissions.contains("users.manage");
    }

    public record QuestionBankWorkspaceInput(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String topicId,
            String status,
            String quizKind,
            Boolean includeQuestions,
            Integer page,
            Integer size
    ) {

        static QuestionBankWorkspaceInput empty() {
            return new QuestionBankWorkspaceInput(null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record AccessManagementWorkspaceInput(
            String tenantId,
            String search,
            String roleId,
            String status,
            Integer page,
            Integer size
    ) {

        static AccessManagementWorkspaceInput empty() {
            return new AccessManagementWorkspaceInput(null, null, null, null, null, null);
        }
    }
}
