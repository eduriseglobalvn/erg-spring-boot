package com.erg.ergspringboot.controller.lms.graphql;

import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsQueryOperations;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentRead;
import com.erg.ergspringboot.lms.authorization.LmsAssignmentProgressRead;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import java.util.List;

@Controller
public class LmsHomeworkWorkspaceGraphQlController {

    private final LmsQueryOperations queries;
    private final GraphQlAccessSupport accessSupport;
    private final LmsAssignmentAccessPolicy assignmentAccess;

    public LmsHomeworkWorkspaceGraphQlController(
            LmsQueryOperations queries,
            GraphQlAccessSupport accessSupport,
            LmsAssignmentAccessPolicy assignmentAccess
    ) {
        this.queries = queries;
        this.accessSupport = accessSupport;
        this.assignmentAccess = assignmentAccess;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "teacherHomeworkWorkspace")
    @LmsAssignmentRead
    public TeacherHomeworkWorkspaceResponse teacherHomeworkWorkspace(@Argument TeacherHomeworkWorkspaceInput input) {
        TeacherHomeworkWorkspaceInput safeInput = input == null ? TeacherHomeworkWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.assignment.read", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        String teacherId = requirePrincipalUserId(principal);

        return queries.teacherHomeworkWorkspace(
                tenantId,
                teacherId,
                safeInput.status(),
                safeInput.page(),
                safeInput.size()
        );
    }

    @SchemaMapping(typeName = "LmsQuery", field = "assignmentProgressWorkspace")
    @LmsAssignmentProgressRead
    public AssignmentProgressWorkspaceResponse assignmentProgressWorkspace(@Argument AssignmentProgressWorkspaceInput input) {
        AssignmentProgressWorkspaceInput safeInput = input == null ? AssignmentProgressWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.assignment.progress.read", "lms.grade.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        // N8: same class + education-unit policy as REST, evaluated before the (cached) read model runs.
        if (!accessSupport.isWildcardPrincipal(principal)) {
            assignmentAccess.readableAssignment(tenantId, safeInput.assignmentId(), new UserAccessContext(
                    principal.userId(), tenantId, null, null, null, null, AccessScopeAssembler.from(principal)));
        }

        return queries.assignmentProgressWorkspace(
                tenantId,
                safeInput.assignmentId(),
                safeInput.attemptStatus(),
                safeInput.page(),
                safeInput.size()
        );
    }

    @SchemaMapping(typeName = "LmsQuery", field = "studentGroups")
    @LmsAssignmentRead
    public StudentGroupWorkspaceResponse studentGroups(@Argument StudentGroupsInput input) {
        StudentGroupsInput safeInput = input == null ? StudentGroupsInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.assignment.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        String requestedClassId = accessSupport.blankToNull(safeInput.classId());
        if (requestedClassId != null) {
            accessSupport.assertClassScope(principal, requestedClassId, null);
        }
        return queries.studentGroups(
                tenantId,
                requestedClassId,
                readableClassIds(principal),
                accessSupport.blankToNull(principal == null ? null : principal.userId())
        );
    }

    private String requirePrincipalUserId(ErgPrincipal principal) {
        String userId = accessSupport.blankToNull(principal == null ? null : principal.userId());
        if (userId == null) {
            throw new ForbiddenException("GraphQL teacher homework workspace requires a scoped principal.");
        }
        return userId;
    }

    private List<String> readableClassIds(ErgPrincipal principal) {
        if (accessSupport.isWildcardPrincipal(principal)) {
            return List.of("*");
        }
        List<String> managedClassIds = AccessScopeAssembler.from(principal).managedIds(AccessScopeSet.CLASS);
        if (managedClassIds.contains("*")) {
            return List.of("*");
        }
        return managedClassIds;
    }

    public record TeacherHomeworkWorkspaceInput(
            String tenantId,
            String status,
            Integer page,
            Integer size
    ) {

        static TeacherHomeworkWorkspaceInput empty() {
            return new TeacherHomeworkWorkspaceInput(null, null, null, null);
        }
    }

    public record AssignmentProgressWorkspaceInput(
            String tenantId,
            @jakarta.validation.constraints.NotBlank(message = "assignmentId is required") String assignmentId,
            String attemptStatus,
            @jakarta.validation.constraints.Min(0) Integer page,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer size
    ) {

        static AssignmentProgressWorkspaceInput empty() {
            return new AssignmentProgressWorkspaceInput(null, null, null, null, null);
        }
    }

    public record StudentGroupsInput(
            String tenantId,
            String classId
    ) {

        static StudentGroupsInput empty() {
            return new StudentGroupsInput(null, null);
        }
    }
}
