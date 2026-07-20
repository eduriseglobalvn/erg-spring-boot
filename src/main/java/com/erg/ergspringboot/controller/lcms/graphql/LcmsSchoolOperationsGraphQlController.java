package com.erg.ergspringboot.controller.lcms.graphql;

import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolDirectoryWorkspace;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses.SchoolOperationsWorkspace;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.school.SchoolOperationsReadService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.springframework.stereotype.Controller;

@Controller
public class LcmsSchoolOperationsGraphQlController {
    private final GraphQlAccessSupport accessSupport;
    private final SchoolOperationsReadService readService;

    public LcmsSchoolOperationsGraphQlController(GraphQlAccessSupport accessSupport, SchoolOperationsReadService readService) {
        this.accessSupport = accessSupport;
        this.readService = readService;
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "schoolDirectory")
    public SchoolDirectoryWorkspace schoolDirectory(@Argument SchoolDirectoryInput input) {
        SchoolDirectoryInput safe = input == null ? SchoolDirectoryInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.school.read", "lms.school.update", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safe.tenantId());
        List<String> scopes = accessSupport.csv(accessSupport.attribute(principal, "managedEducationUnitIds"));
        boolean allScope = accessSupport.isWildcardPrincipal(principal) || scopes.contains("*");
        return readService.directory(tenantId, scopes, allScope, safe.search(), safe.status(), safe.schoolType(), safe.page(), safe.size());
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "schoolOperations")
    public SchoolOperationsWorkspace schoolOperations(
            @Argument SchoolOperationsInput input,
            DataFetchingFieldSelectionSet selectionSet) {
        if (input == null || input.schoolId() == null || input.schoolId().isBlank()) {
            throw new IllegalArgumentException("schoolId is required.");
        }
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.school.read", "lms.school.update", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        java.util.Set<String> selected = selectionSet.getFields().stream()
                .map(field -> field.getQualifiedName().substring(field.getQualifiedName().lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toSet());
        return readService.operations(tenantId, input.schoolId(),
                accessSupport.isAllowed(principal, "lms.school.create", "*"),
                accessSupport.isAllowed(principal, "lms.school.update", "*"),
                accessSupport.isAllowed(principal, "lms.school.archive", "*"),
                accessSupport.isAllowed(principal, "lms.class.manage", "*"), selected);
    }

    public record SchoolDirectoryInput(String tenantId, String search, String status, String schoolType, Integer page, Integer size) {
        static SchoolDirectoryInput empty() { return new SchoolDirectoryInput(null, null, null, null, null, null); }
    }

    public record SchoolOperationsInput(String tenantId, String schoolId) {}
}
