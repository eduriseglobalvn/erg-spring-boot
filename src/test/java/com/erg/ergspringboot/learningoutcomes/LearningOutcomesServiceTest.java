package com.erg.ergspringboot.learningoutcomes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.CreateLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.EvaluateCompletionRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.IssueCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.PublishLearningPathRequest;
import com.erg.ergspringboot.learningoutcomes.LearningOutcomesRequests.RevokeCertificateRequest;
import com.erg.ergspringboot.learningoutcomes.OutcomeCertificateIssued;
import com.erg.ergspringboot.learningoutcomes.internal.LearningOutcomesService;
import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.model.learningoutcomes.Competency;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathCompletion;
import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import com.erg.ergspringboot.model.learningoutcomes.LearningPathNode;
import com.erg.ergspringboot.model.learningoutcomes.OutcomeCertificate;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyAttainmentRepository;
import com.erg.ergspringboot.repository.learningoutcomes.CompetencyRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningOutcomeCommandReceiptRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathNodeRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathRepository;
import com.erg.ergspringboot.repository.learningoutcomes.LearningPathCompletionRepository;
import com.erg.ergspringboot.repository.learningoutcomes.OutcomeCertificateRepository;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class LearningOutcomesServiceTest {
    @Mock private LearningPathRepository paths;
    @Mock private LearningPathNodeRepository nodes;
    @Mock private LearningPathCompletionRepository completions;
    @Mock private CompetencyRepository competencies;
    @Mock private CompetencyAttainmentRepository attainments;
    @Mock private OutcomeCertificateRepository certificates;
    @Mock private LearningOutcomeCommandReceiptRepository receipts;
    @Mock private ApplicationEventPublisher events;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AuditOperations audit;

    private AutoCloseable mocks;
    private LearningOutcomesService service;
    private UserAccessContext access;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new LearningOutcomesService(paths, nodes, completions, competencies, attainments, certificates,
                receipts, events, audit);
        access = new UserAccessContext("user-1", "tenant-1", List.of("*"), List.of("*"), null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("user-1", "session", "tenant-1", List.of("teacher"),
                        List.of("learning.outcomes.manage"), List.of(), List.of(), "user", "teacher", Map.of()), "token"));
        when(nodes.findAllByTenantIdAndPathIdOrderBySortOrderAsc(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void mutationRequiresBoundedIdempotencyKey() {
        assertThatThrownBy(() -> service.createPath(access, new CreateLearningPathRequest("Path", null, null, null), " ", "corr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("idempotencyKey");
        verify(paths, never()).saveAndFlush(any());
    }

    @Test
    void createPathPublishesEventAndSameRequestReplays() {
        LearningPath path = persistedPath();
        when(paths.saveAndFlush(any(LearningPath.class))).thenAnswer(invocation -> {
            LearningPath value = invocation.getArgument(0);
            value.setId("path-1");
            value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            return value;
        });
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));

        var first = service.createPath(access, new CreateLearningPathRequest("Path", "desc", null, null), "key-1", "corr-1");
        LearningOutcomeCommandReceipt receipt = new LearningOutcomeCommandReceipt();
        receipt.setTenantId("tenant-1");
        receipt.setOperation("CREATE_PATH");
        receipt.setIdempotencyKey("key-1");
        receipt.setPayloadHash("unused");
        receipt.setAggregateType("LEARNING_PATH");
        receipt.setAggregateId("path-1");
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "key-1", "CREATE_PATH"))
                .thenReturn(Optional.of(receipt));
        // The actual hash is intentionally different only when a caller changes payload;
        // replay uses the same request and therefore needs the hash saved by the service.
        var savedReceipt = org.mockito.ArgumentCaptor.forClass(LearningOutcomeCommandReceipt.class);
        verify(receipts).saveAndFlush(savedReceipt.capture());
        receipt.setPayloadHash(savedReceipt.getValue().getPayloadHash());

        var replay = service.createPath(access, new CreateLearningPathRequest("Path", "desc", null, null), "key-1", "corr-1");
        assertThat(first.id()).isEqualTo("path-1");
        assertThat(replay.id()).isEqualTo("path-1");
        verify(events).publishEvent(any(LearningPathCreated.class));
    }

    @Test
    void mutationEventAlwaysHasCorrelationIdWhenRequestDoesNotProvideOne() {
        LearningPath path = persistedPath();
        when(paths.saveAndFlush(any(LearningPath.class))).thenAnswer(invocation -> {
            LearningPath value = invocation.getArgument(0);
            value.setId("path-1");
            value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            return value;
        });
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));

        service.createPath(access, new CreateLearningPathRequest("Path", null, null, null), "key-correlation", null);

        var event = org.mockito.ArgumentCaptor.forClass(LearningPathCreated.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().correlationId()).isNotBlank();
        assertThat(event.getValue().causationId()).isEqualTo(event.getValue().correlationId());
    }

    @Test
    void conflictingIdempotencyPayloadFailsClosed() {
        LearningOutcomeCommandReceipt receipt = new LearningOutcomeCommandReceipt();
        receipt.setPayloadHash("different");
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "key-1", "CREATE_PATH"))
                .thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.createPath(access, new CreateLearningPathRequest("Path", null, null, null), "key-1", "corr"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("idempotency");
    }

    @Test
    void publishRejectsStalePathVersion() {
        LearningPath path = persistedPath();
        path.setStatus("DRAFT");
        path.setRecordVersion(4L);
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(nodes.findAllByTenantIdAndPathIdOrderBySortOrderAsc("tenant-1", "path-1"))
                .thenReturn(List.of(new LearningPathNode()));

        assertThatThrownBy(() -> service.publishPath(access, "path-1", 3L, "publish-1", "corr"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("changed");
    }

    @Test
    void crossLearnerReadFailsWithoutManagePermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("user-1", "session", "tenant-1", List.of("student"),
                        List.of("learning.outcomes.read"), List.of(), List.of(), "user", "student", Map.of()), "token"));
        assertThatThrownBy(() -> service.certificates(access, "user-2"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void competencyReadRejectsEducationUnitOutsideManagedScope() {
        UserAccessContext scopedAccess = new UserAccessContext(
                "teacher-1", "tenant-1", List.of("unit-1"), List.of(), "unit-1", null);
        Competency competency = new Competency();
        competency.setId("competency-1");
        competency.setTenantId("tenant-1");
        competency.setCode("ERG-01");
        competency.setName("Outside scope");
        competency.setStatus("ACTIVE");
        competency.setEducationUnitId("unit-2");
        when(competencies.findByTenantIdAndId("tenant-1", "competency-1"))
                .thenReturn(Optional.of(competency));

        assertThatThrownBy(() -> service.competency(scopedAccess, "competency-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void attainmentRejectsClassThatDoesNotMatchLearningPath() {
        LearningPath path = persistedPath();
        path.setStatus("PUBLISHED");
        path.setEducationUnitId("unit-1");
        path.setClassId("class-1");
        Competency competency = new Competency();
        competency.setId("competency-1");
        competency.setTenantId("tenant-1");
        competency.setCode("ERG-01");
        competency.setName("Competency");
        competency.setStatus("ACTIVE");
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(competencies.findByTenantIdAndId("tenant-1", "competency-1"))
                .thenReturn(Optional.of(competency));

        var request = new LearningOutcomesRequests.RecordAttainmentRequest(
                "user-1", "competency-1", "path-1", "class-2", "evidence-1");
        assertThatThrownBy(() -> service.recordAttainment(access, request, "attainment-1", "corr"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("class");
        verify(attainments, never()).saveAndFlush(any());
    }

    @Test
    void certificateReadDoesNotLeakCertificatesOutsideManagedClassScope() {
        UserAccessContext scopedAccess = new UserAccessContext(
                "teacher-1", "tenant-1", List.of("unit-1"), List.of("class-1"), "unit-1", "class-1");
        OutcomeCertificate certificate = new OutcomeCertificate();
        certificate.setId("certificate-1");
        certificate.setTenantId("tenant-1");
        certificate.setLearnerId("learner-1");
        certificate.setLearningPathId("path-2");
        certificate.setCertificateNumber("CERT-1");
        certificate.setStatus("ISSUED");
        certificate.setEvidenceRef("evidence");
        certificate.setIssuedAt(OffsetDateTime.now());
        LearningPath outOfScopePath = persistedPath();
        outOfScopePath.setId("path-2");
        outOfScopePath.setEducationUnitId("unit-1");
        outOfScopePath.setClassId("class-2");
        when(certificates.findTop100ByTenantIdAndLearnerIdOrderByIssuedAtDesc("tenant-1", "learner-1"))
                .thenReturn(List.of(certificate));
        when(paths.findByTenantIdAndId("tenant-1", "path-2")).thenReturn(Optional.of(outOfScopePath));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("teacher-1", "session", "tenant-1", List.of("teacher"),
                        List.of("learning.outcomes.manage"), List.of(), List.of(), "user", "teacher", Map.of()),
                "token", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));

        assertThat(service.certificates(scopedAccess, "learner-1")).isEmpty();
    }

    @Test
    void publishChecksNodeExistenceWithBoundedRepositoryProbe() {
        LearningPath path = persistedPath();
        path.setStatus("DRAFT");
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(nodes.existsByTenantIdAndPathId("tenant-1", "path-1")).thenReturn(false);

        assertThatThrownBy(() -> service.publishPath(access, "path-1", 0L, "publish-2", "corr"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one node");
        verify(nodes).existsByTenantIdAndPathId("tenant-1", "path-1");
        verify(nodes, never()).findAllByTenantIdAndPathIdOrderBySortOrderAsc("tenant-1", "path-1");
    }

    @Test
    void completionEvaluationPersistsProgressAndReplaysIdempotently() {
        LearningPath path = persistedPath();
        path.setStatus("PUBLISHED");
        path.setRecordVersion(2L);
        LearningPathNode requiredNode = node("content-1", true, 1);
        LearningPathNode optionalNode = node("content-2", false, 2);
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(nodes.findTop100ByTenantIdAndPathIdOrderBySortOrderAsc("tenant-1", "path-1"))
                .thenReturn(List.of(requiredNode, optionalNode));
        when(completions.findByTenantIdAndLearnerIdAndLearningPathId("tenant-1", "user-1", "path-1"))
                .thenReturn(Optional.empty());
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "completion-1", "EVALUATE_COMPLETION"))
                .thenReturn(Optional.empty());
        when(completions.saveAndFlush(any(LearningPathCompletion.class))).thenAnswer(invocation -> {
            LearningPathCompletion value = invocation.getArgument(0);
            value.setId("completion-1");
            value.setRecordVersion(0L);
            return value;
        });

        var request = new EvaluateCompletionRequest("user-1", 2L, List.of("content-1"), "evidence-1");
        var first = service.evaluateCompletion(access, "path-1", request, "completion-1", null);

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.progressPercent()).isEqualTo(100);
        assertThat(first.completedNodeCount()).isEqualTo(1);
        var receiptCaptor = org.mockito.ArgumentCaptor.forClass(LearningOutcomeCommandReceipt.class);
        verify(receipts).saveAndFlush(receiptCaptor.capture());
        LearningOutcomeCommandReceipt savedReceipt = receiptCaptor.getValue();
        LearningPathCompletion savedCompletion = new LearningPathCompletion();
        savedCompletion.setId("completion-1");
        savedCompletion.setTenantId("tenant-1");
        savedCompletion.setLearnerId("user-1");
        savedCompletion.setLearningPathId("path-1");
        savedCompletion.setStatus("COMPLETED");
        savedCompletion.setRequiredNodeCount(1);
        savedCompletion.setCompletedNodeCount(1);
        savedCompletion.setProgressPercent(100);
        savedCompletion.setEvidenceRef("evidence-1");
        savedCompletion.setEvaluatedAt(OffsetDateTime.now());
        savedCompletion.setEvaluatedBy("user-1");
        savedCompletion.setRecordVersion(0L);
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "completion-1", "EVALUATE_COMPLETION"))
                .thenReturn(Optional.of(savedReceipt));
        when(completions.findByTenantIdAndId("tenant-1", "completion-1"))
                .thenReturn(Optional.of(savedCompletion));

        var replay = service.evaluateCompletion(access, "path-1", request, "completion-1", "different-correlation");

        assertThat(replay.id()).isEqualTo(first.id());
        verify(completions).saveAndFlush(any(LearningPathCompletion.class));
    }

    @Test
    void attainmentRejectsCompetencyOutsideCurrentEducationUnitScope() {
        UserAccessContext scopedAccess = new UserAccessContext(
                "teacher-1", "tenant-1", List.of("unit-1"), List.of(), "unit-1", null);
        Competency competency = new Competency();
        competency.setId("competency-1");
        competency.setTenantId("tenant-1");
        competency.setCode("ERG-01");
        competency.setName("Outside scope");
        competency.setEducationUnitId("unit-2");
        when(competencies.findByTenantIdAndId("tenant-1", "competency-1"))
                .thenReturn(Optional.of(competency));

        assertThatThrownBy(() -> service.recordAttainment(scopedAccess,
                new LearningOutcomesRequests.RecordAttainmentRequest(
                        "teacher-1", "competency-1", null, null, "evidence-1"),
                "attainment-scope", "corr"))
                .isInstanceOf(ForbiddenException.class);
        verify(attainments, never()).saveAndFlush(any());
    }

    @Test
    void certificateRequiresCompletionAndGetsStableVerificationIdentifier() {
        LearningPath path = persistedPath();
        path.setStatus("PUBLISHED");
        LearningPathCompletion completion = new LearningPathCompletion();
        completion.setTenantId("tenant-1");
        completion.setLearnerId("user-1");
        completion.setLearningPathId("path-1");
        completion.setStatus("COMPLETED");
        completion.setRecordVersion(0L);
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(completions.findByTenantIdAndLearnerIdAndLearningPathId("tenant-1", "user-1", "path-1"))
                .thenReturn(Optional.of(completion));
        when(certificates.findByTenantIdAndLearnerIdAndLearningPathId("tenant-1", "user-1", "path-1"))
                .thenReturn(Optional.empty());
        when(certificates.findByTenantIdAndCertificateNumber("tenant-1", "CERT-1"))
                .thenReturn(Optional.empty());
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "certificate-1", "ISSUE_CERTIFICATE"))
                .thenReturn(Optional.empty());
        when(certificates.saveAndFlush(any(OutcomeCertificate.class))).thenAnswer(invocation -> {
            OutcomeCertificate value = invocation.getArgument(0);
            value.setId("certificate-1");
            value.setRecordVersion(0L);
            return value;
        });

        var response = service.issueCertificate(access,
                new IssueCertificateRequest("user-1", "path-1", "CERT-1", "evidence-1", null),
                "certificate-1", null);

        assertThat(response.verificationId()).isNotBlank();
        assertThat(response.status()).isEqualTo("ISSUED");
        verify(events).publishEvent(any(OutcomeCertificateIssued.class));
    }

    @Test
    void certificateCanBeRevokedAndVerifiedByVerificationIdentifier() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("user-1", "session", "tenant-1", List.of("teacher"),
                        List.of("learning.outcomes.manage"), List.of(), List.of(), "user", "teacher", Map.of()),
                "token", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
        LearningPath path = persistedPath();
        path.setStatus("PUBLISHED");
        OutcomeCertificate certificate = new OutcomeCertificate();
        certificate.setId("certificate-1");
        certificate.setTenantId("tenant-1");
        certificate.setLearnerId("user-1");
        certificate.setLearningPathId("path-1");
        certificate.setCertificateNumber("CERT-1");
        certificate.setVerificationId("verify-1");
        certificate.setStatus("ISSUED");
        certificate.setEvidenceRef("evidence-1");
        certificate.setIssuedAt(OffsetDateTime.now());
        certificate.setRecordVersion(0L);
        when(paths.findByTenantIdAndId("tenant-1", "path-1")).thenReturn(Optional.of(path));
        when(certificates.findByTenantIdAndId("tenant-1", "certificate-1")).thenReturn(Optional.of(certificate));
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "revoke-1", "REVOKE_CERTIFICATE"))
                .thenReturn(Optional.empty());
        when(certificates.saveAndFlush(any(OutcomeCertificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var revoked = service.revokeCertificate(access, "certificate-1",
                new RevokeCertificateRequest(0L, "administrative correction"), "revoke-1", null);

        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(revoked.revokedBy()).isEqualTo("user-1");
        assertThat(revoked.revocationReason()).isEqualTo("administrative correction");
        when(certificates.findByTenantIdAndVerificationId("tenant-1", "verify-1"))
                .thenReturn(Optional.of(certificate));
        assertThat(service.verifyCertificate(access, "verify-1").status()).isEqualTo("REVOKED");
    }

    private LearningPathNode node(String contentRefId, boolean required, int sortOrder) {
        LearningPathNode node = new LearningPathNode();
        node.setId("node-" + contentRefId);
        node.setTenantId("tenant-1");
        node.setPathId("path-1");
        node.setContentRefId(contentRefId);
        node.setContentKind("LESSON");
        node.setSortOrder(sortOrder);
        node.setRequired(required);
        return node;
    }

    private LearningPath persistedPath() {
        LearningPath path = new LearningPath();
        path.setId("path-1");
        path.setTenantId("tenant-1");
        path.setTitle("Path");
        path.setStatus("DRAFT");
        path.setRecordVersion(0L);
        path.setCreatedAt(OffsetDateTime.now());
        path.setUpdatedAt(OffsetDateTime.now());
        return path;
    }
}
