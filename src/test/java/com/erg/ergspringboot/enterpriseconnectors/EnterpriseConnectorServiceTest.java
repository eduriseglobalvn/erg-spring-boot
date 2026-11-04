package com.erg.ergspringboot.enterpriseconnectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.DeprovisionRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.ProviderKind;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RegisterConnectorRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RunRequest;
import com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorProviderRegistry;
import com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorService;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.platform.security.AuthenticationRequiredException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnector;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceipt;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceiptRepository;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRepository;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EnterpriseConnectorServiceTest {
    @Mock private EnterpriseConnectorRepository connectors;
    @Mock private EnterpriseConnectorRunRepository runs;
    @Mock private EnterpriseConnectorCommandReceiptRepository receipts;
    @Mock private EnterpriseConnectorProviderRegistry registry;
    @Mock private EnterpriseConnectorProviderPort provider;
    @Mock private ApplicationEventPublisher events;
    @Mock private AuditOperations audit;

    private AutoCloseable mocks;
    private EnterpriseConnectorService service;
    private UserAccessContext access;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new EnterpriseConnectorService(connectors, runs, receipts, registry, events, audit,
                new ImmediateTransactionManager());
        stored.clear();
        when(runs.saveAndFlush(any(EnterpriseConnectorRun.class))).thenAnswer(invocation -> {
            EnterpriseConnectorRun value = invocation.getArgument(0);
            if (value.getId() == null) value.setId("run-" + value.getMode().toLowerCase() + "-" + stored.size());
            value.setRecordVersion(value.getRecordVersion() == null ? 0L : value.getRecordVersion());
            if (value.getCreatedAt() == null) value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            stored.put(value.getId(), value);
            return value;
        });
        when(runs.findByTenantIdAndId(any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.<String>getArgument(1))));
        access = new UserAccessContext("user-1", "tenant-1", List.of("*"), List.of("*"), null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ErgPrincipal.authenticated("user-1", "session-1", "tenant-1", List.of("admin"),
                        List.of("enterprise.connectors.manage", "enterprise.connectors.sync"),
                        List.of(), List.of(), "user", "admin", Map.of()),
                "token", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(registry.require("REFERENCE")).thenReturn(provider);
        when(provider.supports(ProviderKind.SIS)).thenReturn(true);
        when(provider.supports(ProviderKind.SCIM)).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void registerIsTenantScopedRedactsSecretAndReplaysIdempotently() {
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey("tenant-1", "REGISTER_CONNECTOR", "key-1"))
                .thenReturn(Optional.empty());
        when(connectors.findByTenantIdAndProviderKey("tenant-1", "sis-1")).thenReturn(Optional.empty());
        when(connectors.saveAndFlush(any(EnterpriseConnector.class))).thenAnswer(invocation -> {
            EnterpriseConnector value = invocation.getArgument(0);
            value.setId("connector-1");
            value.setRecordVersion(0L);
            value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            return value;
        });

        var request = new RegisterConnectorRequest(ProviderKind.SIS, "reference", "sis-1", "SIS sandbox",
                "secret://vault/tenant-1/sis", "https://sandbox.invalid", "v1", Map.of("email", "mail"));
        var created = service.register(access, request, "key-1", "corr-1");

        assertThat(created.id()).isEqualTo("connector-1");
        assertThat(created.secretRef()).isEqualTo("secret://***");
        verify(receipts).saveAndFlush(any(EnterpriseConnectorCommandReceipt.class));
    }

    @Test
    void dryRunSyncAndExplicitDeprovisionProduceBoundedResults() {
        EnterpriseConnector connector = connector();
        when(connectors.findByTenantIdAndId("tenant-1", "connector-1")).thenReturn(Optional.of(connector));
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        when(provider.preview(any(), any())).thenReturn(new EnterpriseConnectorProviderPort.PreviewResult(
                2, 1, 1, List.of("external-1", "external-2")));
        when(provider.sync(any(), any())).thenReturn(new EnterpriseConnectorProviderPort.SyncResult(
                3, 2, 1, 0, 0, "SUCCEEDED", null));
        when(provider.deprovision(any(), any())).thenReturn(new EnterpriseConnectorProviderPort.DeprovisionResult(
                2, 1, "DEPROVISIONED"));

        var preview = service.dryRun(access, "connector-1", new RunRequest(0L, null), "dry-1", "corr-1");
        var sync = service.sync(access, "connector-1", new RunRequest(0L, null), "sync-1", "corr-1");
        var deprovision = service.deprovision(access, "connector-1",
                new DeprovisionRequest(0L, List.of("a", "a", "b"), "offboarding"), "deprov-1", "corr-1");

        assertThat(preview.additions()).isEqualTo(2);
        assertThat(preview.total()).isEqualTo(4);
        assertThat(sync.processed()).isEqualTo(3);
        assertThat(sync.failed()).isZero();
        assertThat(deprovision.requested()).isEqualTo(2);
        assertThat(deprovision.deprovisioned()).isEqualTo(2);
        verify(provider).deprovision(any(), any());
    }

    @Test
    void missingTenantFailsClosedBeforeRepositoryAccess() {
        UserAccessContext tenantless = new UserAccessContext("user-1", " ", List.of(), List.of(), null, null);

        assertThatThrownBy(() -> service.connectors(tenantless))
                .isInstanceOf(AuthenticationRequiredException.class);
        verify(connectors, never()).findTop100ByTenantIdOrderByUpdatedAtDesc(any());
    }

    @Test
    void conflictingIdempotencyKeyCannotReplayAnotherPayload() {
        EnterpriseConnectorCommandReceipt receipt = new EnterpriseConnectorCommandReceipt();
        receipt.setTenantId("tenant-1");
        receipt.setOperation("REGISTER_CONNECTOR");
        receipt.setIdempotencyKey("same");
        receipt.setPayloadHash("b".repeat(64));
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey("tenant-1", "REGISTER_CONNECTOR", "same"))
                .thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.register(access,
                new RegisterConnectorRequest(ProviderKind.SCIM, "reference", "scim-1", "SCIM",
                        "secret://vault/scim", null, "v1", Map.of("id", "externalId")), "same", "corr-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("idempotency");
    }

    @Test
    void providerIsCalledOutsideAnyDatabaseTransaction() {
        when(connectors.findByTenantIdAndId("tenant-1", "connector-1")).thenReturn(Optional.of(connector()));
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        when(provider.sync(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(stored.values()).singleElement().extracting(EnterpriseConnectorRun::getStatus).isEqualTo("RUNNING");
            return new EnterpriseConnectorProviderPort.SyncResult(1, 1, 0, 0, 99, "SUCCEEDED", null);
        });

        var sync = service.sync(access, "connector-1", new RunRequest(0L, null), "sync-1", "corr-1");

        assertThat(sync.status()).isEqualTo("SUCCEEDED");
        assertThat(sync.retryCount()).as("adapter retry count is ignored").isZero();
        verify(receipts).saveAndFlush(any(EnterpriseConnectorCommandReceipt.class));
    }

    @Test
    void retryCreatesLinkedRunAndIncrementsFromTheParentCount() {
        when(connectors.findByTenantIdAndId("tenant-1", "connector-1")).thenReturn(Optional.of(connector()));
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        EnterpriseConnectorRun parent = failedRun("run-failed", 1);
        when(provider.sync(any(), any())).thenReturn(new EnterpriseConnectorProviderPort.SyncResult(
                5, 0, 0, 5, 0, "FAILED", "UPSTREAM_DOWN"));

        var retried = service.retry(access, "connector-1", "run-failed",
                new com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest(0L), "retry-1", "c");

        assertThat(retried.runId()).isNotEqualTo("run-failed");
        assertThat(retried.mode()).isEqualTo("RETRY");
        assertThat(retried.retryCount()).isEqualTo(2);
        assertThat(stored.get(retried.runId()).getParentRunId()).isEqualTo("run-failed");
        assertThat(parent.getStatus()).isEqualTo("FAILED");
        assertThat(parent.getRetryCount()).isEqualTo(1);
        assertThat(parent.getProcessed()).isEqualTo(7);
    }

    @Test
    void retryStopsAtTheLimitEvenWhenTheAdapterReportsZeroRetries() {
        when(connectors.findByTenantIdAndId("tenant-1", "connector-1")).thenReturn(Optional.of(connector()));
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        failedRun("run-failed", 3);

        assertThatThrownBy(() -> service.retry(access, "connector-1", "run-failed",
                new com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest(0L), "retry-2", "c"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("limit");
        verify(provider, never()).sync(any(), any());
    }

    @Test
    void retryReplayIsReturnedBeforeStateChecks() {
        when(connectors.findByTenantIdAndId("tenant-1", "connector-1")).thenReturn(Optional.of(connector()));
        EnterpriseConnectorRun parent = failedRun("run-failed", 0);
        parent.setStatus("SUCCEEDED");
        EnterpriseConnectorRun earlierRetry = failedRun("run-retry", 1);
        earlierRetry.setStatus("SUCCEEDED");
        String hash = EnterpriseConnectorPolicy.sha256("RETRY\u001fconnector-1\u001frun-failed\u001f0");
        EnterpriseConnectorCommandReceipt receipt = new EnterpriseConnectorCommandReceipt();
        receipt.setPayloadHash(hash);
        receipt.setAggregateId("run-retry");
        when(receipts.findByTenantIdAndOperationAndIdempotencyKey("tenant-1", "RETRY", "retry-1"))
                .thenReturn(Optional.of(receipt));

        var replay = service.retry(access, "connector-1", "run-failed",
                new com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest(0L), "retry-1", "c");

        assertThat(replay.runId()).isEqualTo("run-retry");
        verify(provider, never()).sync(any(), any());
    }

    private EnterpriseConnectorRun failedRun(String id, int retryCount) {
        EnterpriseConnectorRun run = new EnterpriseConnectorRun();
        run.setId(id);
        run.setTenantId("tenant-1");
        run.setConnectorId("connector-1");
        run.setMode("SYNC");
        run.setStatus("FAILED");
        run.setProcessed(7);
        run.setRetryCount(retryCount);
        run.setRequestedBy("user-1");
        run.setRecordVersion(0L);
        run.setCreatedAt(OffsetDateTime.now());
        stored.put(id, run);
        return run;
    }

    private final Map<String, EnterpriseConnectorRun> stored = new java.util.LinkedHashMap<>();

    private static final class ImmediateTransactionManager
            extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { }
    }

    private EnterpriseConnector connector() {
        EnterpriseConnector value = new EnterpriseConnector();
        value.setId("connector-1");
        value.setTenantId("tenant-1");
        value.setProviderKind("SIS");
        value.setAdapterKey("REFERENCE");
        value.setProviderKey("sis-1");
        value.setMappingVersion("v1");
        value.setMappingHash(EnterpriseConnectorPolicy.sha256("mapping"));
        value.setStatus("ACTIVE");
        value.setRecordVersion(0L);
        value.setCreatedAt(OffsetDateTime.now());
        value.setUpdatedAt(OffsetDateTime.now());
        value.setCreatedBy("user-1");
        value.setUpdatedBy("user-1");
        return value;
    }
}
