package com.erg.ergspringboot.controller.learningoutcomes;

import com.erg.ergspringboot.learningoutcomes.LearningOutcomesOperations;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.AddPathNodeRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateCompetencyRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.EvaluateCompletionRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.IssueCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RecordAttainmentRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RevokeCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.AttainmentResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CertificateResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompletionResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.CompetencyResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.LearningPathResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathDetailResponse;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesResponses.PathNodeResponse;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesIssueCertificate;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesRead;
import com.erg.ergspringboot.learningoutcomes.authorization.LearningOutcomesWrite;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** GraphQL adapter; all business behavior remains in LearningOutcomesOperations. */
@Controller
public class LearningOutcomesGraphQlController {
    private final LearningOutcomesOperations operations;
    private final GraphQlAccessSupport accessSupport;

    public LearningOutcomesGraphQlController(LearningOutcomesOperations operations, GraphQlAccessSupport accessSupport) {
        this.operations = operations;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "learningOutcomes")
    @LearningOutcomesRead
    public LearningOutcomesWorkspace learningOutcomes(@Argument LearningOutcomesQueryInput input) {
        LearningOutcomesQueryInput safe = input == null ? new LearningOutcomesQueryInput(null, null, null, null, null) : input;
        ErgPrincipal principal = principal("learning.outcomes.read", "learning.outcomes.manage");
        String tenant = accessSupport.verifiedTenantId(principal, safe.tenantId());
        accessSupport.assertEducationUnitScope(principal, safe.educationUnitId());
        UserAccessContext access = access(principal, tenant);
        String learnerId = safe.learnerId() == null ? principal.userId() : safe.learnerId();
        return new LearningOutcomesWorkspace(
                operations.paths(access, safe.status(), safe.educationUnitId(), safe.classId()),
                operations.competencies(access, safe.educationUnitId()),
                operations.completions(access, learnerId),
                operations.attainments(access, learnerId),
                operations.certificates(access, learnerId));
    }

    @SchemaMapping(typeName = "LmsQuery", field = "learningOutcomePath")
    @LearningOutcomesRead
    public PathDetailResponse learningOutcomePath(@Argument String pathId) {
        ErgPrincipal principal = principal("learning.outcomes.read", "learning.outcomes.manage");
        return operations.path(access(principal, principal.tenantId()), pathId);
    }

    @SchemaMapping(typeName = "Mutation", field = "createLearningPath")
    @LearningOutcomesWrite
    public LearningPathResponse createLearningPath(@Argument CreateLearningPathInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        String tenant = accessSupport.verifiedTenantId(principal, input.tenantId());
        return operations.createPath(access(principal, tenant),
                new CreateLearningPathRequest(input.title(), input.description(), input.educationUnitId(), input.classId()),
                input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "publishLearningPath")
    @LearningOutcomesWrite
    public LearningPathResponse publishLearningPath(@Argument PublishLearningPathInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        return operations.publishPath(access(principal, principal.tenantId()), input.pathId(), input.expectedVersion(),
                input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "addLearningPathNode")
    @LearningOutcomesWrite
    public PathNodeResponse addLearningPathNode(@Argument AddLearningPathNodeInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        return operations.addNode(access(principal, principal.tenantId()), input.pathId(),
                new AddPathNodeRequest(input.contentRefId(), input.contentKind(), input.sortOrder(), input.required(),
                        input.expectedPathVersion()), input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "createCompetency")
    @LearningOutcomesWrite
    public CompetencyResponse createCompetency(@Argument CreateCompetencyInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        String tenant = accessSupport.verifiedTenantId(principal, input.tenantId());
        return operations.createCompetency(access(principal, tenant),
                new CreateCompetencyRequest(input.code(), input.name(), input.description(), input.educationUnitId()),
                input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "recordCompetencyAttainment")
    @LearningOutcomesWrite
    public AttainmentResponse recordCompetencyAttainment(@Argument RecordCompetencyAttainmentInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        return operations.recordAttainment(access(principal, principal.tenantId()),
                new RecordAttainmentRequest(input.learnerId(), input.competencyId(), input.learningPathId(),
                        input.classId(), input.evidenceRef(), input.progressPercent()),
                input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "evaluateLearningPathCompletion")
    @LearningOutcomesWrite
    public CompletionResponse evaluateLearningPathCompletion(@Argument EvaluateCompletionInput input) {
        ErgPrincipal principal = principal("learning.outcomes.manage");
        return operations.evaluateCompletion(access(principal, principal.tenantId()), input.learningPathId(),
                new EvaluateCompletionRequest(input.learnerId(), input.expectedVersion(), input.completedContentRefIds(),
                        input.evidenceRef()), input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "issueOutcomeCertificate")
    @LearningOutcomesIssueCertificate
    public CertificateResponse issueOutcomeCertificate(@Argument IssueOutcomeCertificateInput input) {
        ErgPrincipal principal = principal("learning.outcomes.issue_certificate", "learning.outcomes.manage");
        return operations.issueCertificate(access(principal, principal.tenantId()),
                new IssueCertificateRequest(input.learnerId(), input.learningPathId(), input.certificateNumber(),
                        input.evidenceRef(), parseExpiry(input.expiresAt())), input.idempotencyKey(), correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "revokeOutcomeCertificate")
    @LearningOutcomesIssueCertificate
    public CertificateResponse revokeOutcomeCertificate(@Argument RevokeOutcomeCertificateInput input) {
        ErgPrincipal principal = principal("learning.outcomes.issue_certificate", "learning.outcomes.manage");
        return operations.revokeCertificate(access(principal, principal.tenantId()), input.certificateId(),
                new RevokeCertificateRequest(input.expectedVersion(), input.reason()), input.idempotencyKey(),
                correlation(input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "LmsQuery", field = "verifyOutcomeCertificate")
    @LearningOutcomesRead
    public CertificateResponse verifyOutcomeCertificate(@Argument String verificationId) {
        ErgPrincipal principal = principal("learning.outcomes.read", "learning.outcomes.manage");
        return operations.verifyCertificate(access(principal, principal.tenantId()), verificationId);
    }

    private ErgPrincipal principal(String... permissions) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, permissions);
        return principal;
    }

    private UserAccessContext access(ErgPrincipal principal, String tenant) {
        AccessScopeSet scopes = AccessScopeAssembler.from(principal);
        return new UserAccessContext(principal.userId(), tenant,
                scopes.managedIds(AccessScopeSet.EDUCATION_UNIT), scopes.managedIds(AccessScopeSet.CLASS),
                scopes.currentId(AccessScopeSet.EDUCATION_UNIT).orElse(null),
                scopes.currentId(AccessScopeSet.CLASS).orElse(null));
    }

    private String correlation(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? IdGenerator.newId() : idempotencyKey;
    }

    private OffsetDateTime parseExpiry(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("expiresAt must be an ISO-8601 offset date-time", exception);
        }
    }

    public record LearningOutcomesQueryInput(String tenantId, String educationUnitId, String classId,
                                             String status, String learnerId) {
    }

    public record LearningOutcomesWorkspace(
            List<LearningPathResponse> paths,
            List<CompetencyResponse> competencies,
            List<CompletionResponse> completions,
            List<AttainmentResponse> attainments,
            List<CertificateResponse> certificates) {
    }

    public record CreateLearningPathInput(String tenantId, String title, String description,
                                          String educationUnitId, String classId, String idempotencyKey) {
    }

    public record PublishLearningPathInput(String pathId, long expectedVersion, String idempotencyKey) {
    }

    public record AddLearningPathNodeInput(String pathId, String contentRefId, String contentKind,
                                           int sortOrder, boolean required, long expectedPathVersion,
                                           String idempotencyKey) {
    }

    public record CreateCompetencyInput(String tenantId, String code, String name, String description,
                                        String educationUnitId, String idempotencyKey) {
    }

    public record RecordCompetencyAttainmentInput(String learnerId, String competencyId, String learningPathId,
                                                  String classId, String evidenceRef, Integer progressPercent,
                                                  String idempotencyKey) {
    }

    public record EvaluateCompletionInput(String learnerId, String learningPathId, long expectedVersion,
                                          List<String> completedContentRefIds, String evidenceRef,
                                          String idempotencyKey) {
    }

    public record IssueOutcomeCertificateInput(String learnerId, String learningPathId, String certificateNumber,
                                               String evidenceRef, String expiresAt, String idempotencyKey) {
    }

    public record RevokeOutcomeCertificateInput(String certificateId, long expectedVersion, String reason,
                                                String idempotencyKey) {
    }
}
