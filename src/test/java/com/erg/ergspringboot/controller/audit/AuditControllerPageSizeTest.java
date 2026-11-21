package com.erg.ergspringboot.controller.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditQuery;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class AuditControllerPageSizeTest {

    private final AuditOperations audit = mock(AuditOperations.class);
    private final AuditController controller = new AuditController(audit, new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theEventListIsClampedToTheListPageSizeNotTheExportMaximum() {
        ErgPrincipal principal = new ErgPrincipal("user-1", "session-1", "tenant-a", List.of("admin"), List.of("*"), List.of(),
                List.of("lcms"), "admin", "tenant", Map.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        controller.events(null, null, null, null, null, null, 0, 1000, new MockHttpServletRequest());

        ArgumentCaptor<AuditQuery> query = ArgumentCaptor.forClass(AuditQuery.class);
        verify(audit).query(any(), query.capture());
        assertThat(query.getValue().size()).isEqualTo(AuditController.MAX_EVENTS_PAGE_SIZE);
    }
}
