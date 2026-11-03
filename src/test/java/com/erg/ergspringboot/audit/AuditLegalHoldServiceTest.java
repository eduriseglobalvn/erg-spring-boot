package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.internal.AuditLegalHoldService;
import com.erg.ergspringboot.audit.internal.AuditWriteService;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLegalHoldServiceTest {

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditWriteService writes = mock(AuditWriteService.class);
    private final AuditLegalHoldService service = new AuditLegalHoldService(repository, new ErgPolicyService(), writes);
    private final UserAccessContext access = new UserAccessContext("admin-1", "tenant-a", List.of("*"), List.of(), null, null);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresAuditManage() {
        authenticate(List.of("audit.read"));

        assertThatThrownBy(() -> service.setLegalHold(access, "event-1", true, "req-1"))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).updateLegalHold(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void placesHoldInCallerTenantAndAuditsTheChange() {
        authenticate(List.of("audit.manage"));
        when(repository.updateLegalHold("tenant-a", "event-1", true)).thenReturn(1);

        service.setLegalHold(access, "event-1", true, "req-1");

        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(writes).append(record.capture());
        assertThat(record.getValue().action()).isEqualTo("AUDIT_LEGAL_HOLD_SET");
        assertThat(record.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(record.getValue().details()).isEqualTo(Map.of("legalHold", true));
    }

    @Test
    void unknownOrForeignTenantEventIsNotFound() {
        authenticate(List.of("audit.manage"));
        when(repository.updateLegalHold("tenant-a", "event-x", true)).thenReturn(0);

        assertThatThrownBy(() -> service.setLegalHold(access, "event-x", true, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(writes, never()).append(any());
    }

    private void authenticate(List<String> permissions) {
        ErgPrincipal principal = ErgPrincipal.authenticated("admin-1", "session", "tenant-a", List.of("admin"),
                permissions, List.of(), List.of("*"), "staff", null, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
