package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.internal.AuditQueryService;
import com.erg.ergspringboot.audit.internal.AuditRedactionPolicy;
import com.erg.ergspringboot.audit.persistence.AuditEvent;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.audit.persistence.AuditEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {
    @Mock private AuditEventRepository repository;
    private final AuditRedactionPolicy redaction = new AuditRedactionPolicy();

    @Test
    void filtersByTenantAndManagedScopesAndRedactsAtReadBoundary() {
        AuditEvent event = new AuditEvent();
        event.setId("audit-1");
        event.setTenantId("tenant-a");
        event.setActorId("actor-a");
        event.setScopeType("CLASS");
        event.setScopeId("class-a");
        event.setAction("GRADE_UPDATED");
        event.setResourceType("GRADE");
        event.setOutcome(AuditOutcome.SUCCESS);
        event.setDetails(Map.of("email", "person@example.com"));
        when(repository.search(eq("tenant-a"), any(), any(), any(), any(), any(), any(), eq(false), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 100), 1));

        UserAccessContext access = new UserAccessContext(
                "actor-a", "tenant-a", List.of("unit-a"), List.of("class-a"), "unit-a", "class-a");
        AuditPage page = new AuditQueryService(repository, redaction).query(access, AuditQuery.defaults());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().details().get("email")).asString().startsWith("hmac-sha256:");
        ArgumentCaptor<List<String>> units = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> classes = ArgumentCaptor.forClass(List.class);
        verify(repository).search(eq("tenant-a"), any(), any(), any(), any(), any(), any(), eq(false), units.capture(), classes.capture(), any());
        assertThat(units.getValue()).containsExactly("unit-a");
        assertThat(classes.getValue()).containsExactly("class-a");
    }

    @Test
    void neverPassesNullDatesToTheRepository() {
        when(repository.search(eq("tenant-a"), org.mockito.ArgumentMatchers.notNull(),
                org.mockito.ArgumentMatchers.notNull(), any(), any(), any(), any(), eq(true), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        UserAccessContext access = new UserAccessContext("actor-a", "tenant-a", List.of("*"), List.of(), null, null);

        new AuditQueryService(repository, redaction).query(access, AuditQuery.defaults());

        verify(repository).search(eq("tenant-a"), org.mockito.ArgumentMatchers.notNull(),
                org.mockito.ArgumentMatchers.notNull(), any(), any(), any(), any(), eq(true), any(), any(), any());
    }

    @Test
    void countsTheTenantOnlyWhenMoreRowsExistAndSharesTheCountBriefly() {
        AuditEvent event = new AuditEvent();
        event.setId("audit-1");
        event.setTenantId("tenant-a");
        when(repository.search(eq("tenant-a"), any(), any(), any(), any(), any(), any(), eq(true), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of(event), PageRequest.of(0, 1), true));
        when(repository.countSearch(eq("tenant-a"), any(), any(), any(), any(), any(), any(), eq(true), any(), any()))
                .thenReturn(41L);
        UserAccessContext access = new UserAccessContext("actor-a", "tenant-a", List.of("*"), List.of(), null, null);
        AuditQueryService service = new AuditQueryService(repository, redaction);
        AuditQuery firstPage = new AuditQuery(null, null, null, null, null, null, 0, 1);

        AuditPage page = service.query(access, firstPage);
        service.query(access, firstPage);

        assertThat(page.totalElements()).isEqualTo(41);
        assertThat(page.totalPages()).isEqualTo(41);
        assertThat(page.hasNext()).isTrue();
        verify(repository, org.mockito.Mockito.times(1))
                .countSearch(eq("tenant-a"), any(), any(), any(), any(), any(), any(), eq(true), any(), any());
    }

    @Test
    void failsClosedWhenTenantIdentityIsMissing() {
        AuditQueryService service = new AuditQueryService(repository, redaction);
        UserAccessContext noTenant = new UserAccessContext("actor-a", " ", List.of(), List.of(), null, null);

        assertThatThrownBy(() -> service.query(noTenant, AuditQuery.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }
}
