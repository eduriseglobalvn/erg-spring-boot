package com.erg.ergspringboot.controller.lcms.graphql;

import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lcms.LcmsQueryOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsQuizWorkspaceRead;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL reads for the LCMS quiz workspace + editor hydrate (spec §4.1). Delegates to the read model,
 * enforces field-level permission + tenant scope like the sibling question-bank workspace, and returns
 * the shared {@code *Page} shape so the FE {@code GraphQlPage<T>} maps unchanged.
 */
@Controller
public class LcmsQuizWorkspaceGraphQlController {

    private final LcmsQueryOperations queries;
    private final GraphQlAccessSupport accessSupport;

    public LcmsQuizWorkspaceGraphQlController(
            LcmsQueryOperations queries,
            GraphQlAccessSupport accessSupport
    ) {
        this.queries = queries;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "quizWorkspace")
    @LcmsQuizWorkspaceRead
    public QuizWorkspaceResponse quizWorkspace(@Argument QuizWorkspaceInput input) {
        QuizWorkspaceInput safeInput = input == null ? QuizWorkspaceInput.empty() : input;
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.exercise.read", "lms.quiz.read", "lms.exercise.create", "lms.quiz.create", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, safeInput.tenantId());
        return queries.quizWorkspace(
                tenantId,
                safeInput.search(),
                safeInput.subjectId(),
                safeInput.levelId(),
                safeInput.categoryId(),
                safeInput.status(),
                safeInput.kind(),
                safeInput.page(),
                safeInput.size()
        );
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "quizDraft")
    @LcmsQuizWorkspaceRead
    public QuizDraftDocumentResponse quizDraft(@Argument String quizId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.exercise.read", "lms.quiz.read", "lms.exercise.create", "lms.quiz.create", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        return queries.quizDraft(tenantId, quizId);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "quizVersions")
    @LcmsQuizWorkspaceRead
    public QuizVersionHistoryResponse quizVersions(@Argument String quizId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.exercise.read", "lms.quiz.read", "lms.exercise.create", "lms.quiz.create", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        return queries.quizVersions(tenantId, quizId);
    }

    public record QuizWorkspaceInput(
            String tenantId,
            String search,
            String subjectId,
            String levelId,
            String categoryId,
            String status,
            String kind,
            Integer page,
            Integer size
    ) {

        static QuizWorkspaceInput empty() {
            return new QuizWorkspaceInput(null, null, null, null, null, null, null, null, null);
        }
    }
}
