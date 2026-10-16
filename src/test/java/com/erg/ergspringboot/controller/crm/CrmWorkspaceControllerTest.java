package com.erg.ergspringboot.controller.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateLeadStatusRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.LeadResponse;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.crm.CrmOperations;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CrmWorkspaceControllerTest {

    private CrmOperations service;
    private CrmWorkspaceController controller;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(CrmOperations.class);
        controller = new CrmWorkspaceController(service);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ErgPrincipal(
                        "seo-1", "session-1", "tenant-a", List.of("crm"), List.of("crm.deal.manage"),
                        List.of(), List.of("crm"), "staff", "tenant",
                        Map.of("managedEducationUnitIds", List.of("school-1"))
                ), null, List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLeadStatusUsesAuthenticatedTenantAndActor() {
        UpdateLeadStatusRequest body = new UpdateLeadStatusRequest("negotiating", 3L);
        LeadResponse updated = LeadResponse.empty("lead-1", "school-1", "negotiating", 4L);
        when(service.updateLeadStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("lead-1"),
                org.mockito.ArgumentMatchers.eq(body))).thenReturn(updated);

        ApiResponse<LeadResponse> response = controller.updateLeadStatus(
                "lead-1", body, request("PATCH", "/api/v1/crm/leads/lead-1/status")
        );

        assertThat(response.data()).isSameAs(updated);
        verify(service).updateLeadStatus(
                org.mockito.ArgumentMatchers.argThat(access -> access.tenantId().equals("tenant-a")
                        && access.userId().equals("seo-1") && access.canAccessEducationUnit("school-1")),
                org.mockito.ArgumentMatchers.eq("lead-1"), org.mockito.ArgumentMatchers.eq(body)
        );
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Request-ID", "request-1");
        return request;
    }
}
