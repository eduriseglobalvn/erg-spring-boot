package com.erg.ergspringboot.controller.elearning.graphql;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class StudentDashboardGraphQlController {

    private final StudentDashboardReadService studentDashboardReadService;
    private final GraphQlAccessSupport accessSupport;

    public StudentDashboardGraphQlController(
            StudentDashboardReadService studentDashboardReadService,
            GraphQlAccessSupport accessSupport
    ) {
        this.studentDashboardReadService = studentDashboardReadService;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "studentDashboard")
    @PreAuthorize("hasAnyAuthority('perm:elearning.dashboard.read', 'perm:*')")
    public StudentDashboardResponse studentDashboard(@Argument StudentDashboardInput input) {
        StudentDashboardInput safeInput = input == null ? StudentDashboardInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("GraphQL student dashboard requires an authenticated principal.");
        }
        return studentDashboardReadService.studentDashboard(new StudentDashboardReadScope(
                principal.userId(),
                principal.tenantId(),
                safeInput.tenantId(),
                safeInput.studentUserId(),
                safeList(principal.roles()),
                safeList(principal.permissions()),
                safeList(principal.deniedPermissions()),
                safeList(principal.portals()),
                principal.accountType(),
                principal.accessLevel()
        ));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record StudentDashboardInput(
            String tenantId,
            String studentUserId
    ) {

        static StudentDashboardInput empty() {
            return new StudentDashboardInput(null, null);
        }
    }
}
