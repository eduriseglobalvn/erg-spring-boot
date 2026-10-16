package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.internal.AuditRetentionService;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuditRetentionServiceTest {
    @Test
    void purgesOnlyThroughBoundedMaintenancePath() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0);
        when(repository.deleteExpired(any(), org.mockito.ArgumentMatchers.eq(25))).thenReturn(4);

        AuditRetentionService service = new AuditRetentionService(repository, entityManager, 25, 365);

        assertThat(service.purge(OffsetDateTime.parse("2026-01-01T00:00:00Z"))).isEqualTo(4);
        verify(entityManager).createNativeQuery("select set_config('app.maintenance_mode','true',true)");
        verify(entityManager).createNativeQuery("select set_config('app.audit_retention_job','true',true)");
        verify(repository).deleteExpired(OffsetDateTime.parse("2026-01-01T00:00:00Z"), 25);
    }

    @Test
    void rejectsUnboundedRetentionConfiguration() {
        assertThatThrownBy(() -> new AuditRetentionService(mock(AuditEventRepository.class), mock(EntityManager.class), 0, 365))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditRetentionService(mock(AuditEventRepository.class), mock(EntityManager.class), 25, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
