package com.erg.ergspringboot.controller.elearning.graphql;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.elearning.ElearningOperations;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DashboardReadQuery;
import com.erg.ergspringboot.elearning.authorization.ElearningDashboardRead;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class StudentDashboardGraphQlController {

    private final ElearningOperations elearning;
    private final GraphQlAccessSupport accessSupport;

    public StudentDashboardGraphQlController(
            ElearningOperations elearning,
            GraphQlAccessSupport accessSupport
    ) {
        this.elearning = elearning;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "studentDashboard")
    @ElearningDashboardRead
    public StudentDashboardResponse studentDashboard(@Argument StudentDashboardInput input) {
        StudentDashboardInput safeInput = input == null ? StudentDashboardInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("GraphQL student dashboard requires an authenticated principal.");
        }
        return elearning.studentDashboard(new DashboardReadQuery(
                principal.userId(),
                principal.tenantId(),
                safeInput.tenantId(),
                safeInput.studentUserId(),
                safeList(principal.roles()),
                safeList(principal.permissions()),
                safeList(principal.deniedPermissions()),
                safeList(principal.portals()),
                principal.accountType(),
                principal.accessLevel(),
                safeInput.classId()
        ));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record StudentDashboardInput(
            String tenantId,
            String studentUserId,
            String classId
    ) {

        public StudentDashboardInput(String tenantId, String studentUserId) {
            this(tenantId, studentUserId, null);
        }

        static StudentDashboardInput empty() {
            return new StudentDashboardInput(null, null, null);
        }
    }
}
