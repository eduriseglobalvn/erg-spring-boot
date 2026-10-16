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
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnector;
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnectorCommandReceipt;
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnectorRun;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorCommandReceiptRepository;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorRepository;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorRunRepository;
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
        service = new EnterpriseConnectorService(connectors, runs, receipts, registry, events, audit);
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
        when(runs.saveAndFlush(any(EnterpriseConnectorRun.class))).thenAnswer(invocation -> {
            EnterpriseConnectorRun value = invocation.getArgument(0);
            if (value.getId() == null) value.setId("run-" + value.getMode().toLowerCase());
            value.setRecordVersion(0L);
            value.setCreatedAt(OffsetDateTime.now());
            value.setUpdatedAt(OffsetDateTime.now());
            return value;
        });

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
