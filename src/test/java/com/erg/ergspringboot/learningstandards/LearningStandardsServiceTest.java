package com.erg.ergspringboot.learningstandards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.CreatePackageRequest;
import com.erg.ergspringboot.learningstandards.LearningStandardsRequests.ValidatePackageRequest;
import com.erg.ergspringboot.learningstandards.internal.LearningStandardsService;
import com.erg.ergspringboot.model.learningstandards.LearningPackage;
import com.erg.ergspringboot.model.learningstandards.LearningStandardsCommandReceipt;
import com.erg.ergspringboot.model.learningstandards.XapiStatement;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.AuthenticationRequiredException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.learningstandards.LearningPackageRepository;
import com.erg.ergspringboot.repository.learningstandards.LearningStandardsCommandReceiptRepository;
import com.erg.ergspringboot.repository.learningstandards.LtiRegistrationRepository;
import com.erg.ergspringboot.repository.learningstandards.XapiStatementRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class LearningStandardsServiceTest {
    private static final String MANIFEST = "<manifest identifier=\"course-1\"><schemaVersion>1.2</schemaVersion></manifest>";

    @Mock private LearningPackageRepository packages;
    @Mock private LtiRegistrationRepository registrations;
    @Mock private XapiStatementRepository statements;
    @Mock private LearningStandardsCommandReceiptRepository receipts;
    @Mock private LearningStandardsArtifactStorage storage;
    @Mock private LtiLaunchTokenVerifier ltiVerifier;
    @Mock private ApplicationEventPublisher events;
    @Mock private AuditOperations audit;

    private AutoCloseable mocks;
    private LearningStandardsService service;
    private UserAccessContext access;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new LearningStandardsService(packages, registrations, statements, receipts, storage,
                ltiVerifier, events, audit);
        access = new UserAccessContext("user-1", "tenant-1", List.of("*"), List.of("*"), null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("user-1", "session-1", "tenant-1", List.of("teacher"),
                        List.of("learning.standards.manage"), List.of(), List.of(), "user", "teacher", Map.of()),
                "token", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void packageCreateIsTenantScopedAndReplaySafe() {
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "key-1", "CREATE_PACKAGE"))
                .thenReturn(Optional.empty());
        when(packages.findByTenantIdAndArtifactKey("tenant-1", "artifacts/course-1.zip"))
                .thenReturn(Optional.empty());
        when(packages.saveAndFlush(any(LearningPackage.class))).thenAnswer(invocation -> {
            LearningPackage value = invocation.getArgument(0);
            value.setId("package-1");
            value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            return value;
        });

        var response = service.createPackage(access, new CreatePackageRequest(
                "Course 1", "SCORM_12", "1.0.0", "artifacts/course-1.zip", "a".repeat(64),
                1024L, Hashing.sha256(MANIFEST), "index.html"), "key-1", "corr-1");

        assertThat(response.id()).isEqualTo("package-1");
        assertThat(response.status()).isEqualTo("UPLOADING");
        ArgumentCaptor<LearningStandardsCommandReceipt> receipt = ArgumentCaptor.forClass(LearningStandardsCommandReceipt.class);
        verify(receipts).saveAndFlush(receipt.capture());
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "key-1", "CREATE_PACKAGE"))
                .thenReturn(Optional.of(receipt.getValue()));
        when(packages.findByTenantIdAndId("tenant-1", "package-1"))
                .thenReturn(Optional.of(persistedPackage("package-1", "UPLOADING")));

        var replay = service.createPackage(access, new CreatePackageRequest(
                "Course 1", "SCORM_12", "1.0.0", "artifacts/course-1.zip", "a".repeat(64),
                1024L, Hashing.sha256(MANIFEST), "index.html"), "key-1", "corr-1");
        assertThat(replay.id()).isEqualTo("package-1");
        verify(packages).saveAndFlush(any(LearningPackage.class));
    }

    @Test
    void invalidArtifactIsQuarantinedAndNeverBecomesLaunchable() {
        LearningPackage value = persistedPackage("package-1", "UPLOADING");
        when(packages.findByTenantIdAndId("tenant-1", "package-1")).thenReturn(Optional.of(value));
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "validate-1", "VALIDATE_PACKAGE"))
                .thenReturn(Optional.empty());
        when(storage.head("tenant-1", value.getArtifactKey())).thenReturn(Optional.empty());
        when(packages.saveAndFlush(any(LearningPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.validatePackage(access, "package-1", new ValidatePackageRequest(0L, MANIFEST),
                "validate-1", "corr-1");

        assertThat(response.status()).isEqualTo("QUARANTINED");
        assertThat(response.quarantineReason()).contains("artifact");
        assertThatThrownBy(() -> service.launchPackage(access, "package-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void xapiDuplicateStatementWithDifferentPayloadFailsClosed() {
        XapiStatement existing = new XapiStatement();
        existing.setId("statement-row-1");
        existing.setTenantId("tenant-1");
        existing.setStatementId("statement-1");
        existing.setPayloadSha256("b".repeat(64));
        when(receipts.findByTenantIdAndIdempotencyKeyAndOperation("tenant-1", "statement-1", "XAPI_STATEMENT"))
                .thenReturn(Optional.empty());
        when(statements.findByTenantIdAndStatementId("tenant-1", "statement-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.ingestXapi(access,
                new LearningStandardsRequests.XapiStatementRequest("statement-1", "actor-1",
                        "https://example.test/verb", "https://example.test/object", "a".repeat(64), null),
                null, "corr-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another payload");
        verify(statements, never()).saveAndFlush(any(XapiStatement.class));
    }

    @Test
    void missingTenantFailsClosedBeforeRepositoryAccess() {
        UserAccessContext tenantless = new UserAccessContext("user-1", " ", List.of(), List.of(), null, null);

        assertThatThrownBy(() -> service.packages(tenantless, null))
                .isInstanceOf(AuthenticationRequiredException.class);
        verify(packages, never()).findTop100ByTenantIdOrderByUpdatedAtDesc(any());
    }

    private LearningPackage persistedPackage(String id, String status) {
        LearningPackage value = new LearningPackage();
        value.setId(id);
        value.setTenantId("tenant-1");
        value.setTitle("Course 1");
        value.setStandard("SCORM_12");
        value.setPackageVersion("1.0.0");
        value.setArtifactKey("artifacts/course-1.zip");
        value.setArtifactSha256("a".repeat(64));
        value.setByteSize(1024L);
        value.setManifestSha256(Hashing.sha256(MANIFEST));
        value.setLaunchPath("index.html");
        value.setStatus(status);
        value.setRecordVersion(0L);
        value.setCreatedAt(OffsetDateTime.now());
        value.setUpdatedAt(OffsetDateTime.now());
        value.setCreatedBy("user-1");
        value.setUpdatedBy("user-1");
        return value;
    }
}
