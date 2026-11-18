package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.lms.LmsClassScoreBoardResponses.ClassScoreBoardResponse;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.lms.LmsClassScoreBoardOperations;
import com.erg.ergspringboot.lms.authorization.LmsClassRead;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Scores of a class for its teachers and the training office: needs the grade permission and scope over the class. */
@Controller
public class LmsClassScoreBoardGraphQlController {

    private final LmsClassScoreBoardOperations scoreBoards;
    private final GraphQlAccessSupport accessSupport;
    private final LmsAcademicRecordOperations records;

    public LmsClassScoreBoardGraphQlController(LmsClassScoreBoardOperations scoreBoards, GraphQlAccessSupport accessSupport,
            LmsAcademicRecordOperations records) {
        this.scoreBoards = scoreBoards;
        this.accessSupport = accessSupport;
        this.records = records;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "classScoreBoard")
    @LmsClassRead
    public ClassScoreBoardResponse classScoreBoard(@Argument ClassScoreBoardInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.grade.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        // Scope is checked against the class's real school, as the class workspace does.
        accessSupport.assertClassScope(principal, input.classId(), records.schoolOfClass(tenantId, input.classId()));
        return scoreBoards.scoreBoard(tenantId, input.classId());
    }

    public record ClassScoreBoardInput(String tenantId,
            @jakarta.validation.constraints.NotBlank(message = "classId is required") String classId) {
    }
}
