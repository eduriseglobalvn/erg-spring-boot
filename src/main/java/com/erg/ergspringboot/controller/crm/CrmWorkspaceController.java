package com.erg.ergspringboot.controller.crm;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.ApproveHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateFollowUpRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.FollowUpResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.LeadResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.OpportunityResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateFollowUpCompletionRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateLeadStatusRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.WorkspaceResponse;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.crm.CrmOperations;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmWorkspaceController {

    private final CrmOperations service;

    public CrmWorkspaceController(CrmOperations service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    public ApiResponse<WorkspaceResponse> workspace(HttpServletRequest request) {
        return ok("CRM workspace loaded", service.workspace(UserAccessContext.from(request)), request);
    }

    @PatchMapping("/leads/{leadId}/status")
    public ApiResponse<LeadResponse> updateLeadStatus(
            @PathVariable String leadId, @Valid @RequestBody UpdateLeadStatusRequest body, HttpServletRequest request
    ) {
        return ok("CRM lead status updated", service.updateLeadStatus(UserAccessContext.from(request), leadId, body), request);
    }

    @PostMapping("/opportunities")
    public ApiResponse<OpportunityResponse> createOpportunity(
            @Valid @RequestBody CreateOpportunityRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ok("CRM opportunity created",
                service.createOpportunity(UserAccessContext.from(request), idempotencyKey, body), request);
    }

    @PatchMapping("/opportunities/{opportunityId}")
    public ApiResponse<OpportunityResponse> updateOpportunity(
            @PathVariable String opportunityId, @Valid @RequestBody UpdateOpportunityRequest body,
            HttpServletRequest request
    ) {
        return ok("CRM opportunity updated",
                service.updateOpportunity(UserAccessContext.from(request), opportunityId, body), request);
    }

    @PostMapping("/follow-ups")
    public ApiResponse<FollowUpResponse> createFollowUp(
            @Valid @RequestBody CreateFollowUpRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ok("CRM follow-up created", service.createFollowUp(UserAccessContext.from(request), idempotencyKey, body), request);
    }

    @PatchMapping("/follow-ups/{followUpId}/completion")
    public ApiResponse<FollowUpResponse> updateFollowUpCompletion(
            @PathVariable String followUpId, @Valid @RequestBody UpdateFollowUpCompletionRequest body,
            HttpServletRequest request
    ) {
        return ok("CRM follow-up updated",
                service.updateFollowUpCompletion(UserAccessContext.from(request), followUpId, body), request);
    }

    @PostMapping("/handovers")
    public ApiResponse<HandoverResponse> createHandover(
            @Valid @RequestBody CreateHandoverRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        return ok("CRM handover created", service.createHandover(UserAccessContext.from(request), idempotencyKey, body), request);
    }

    @PostMapping("/handovers/{handoverId}/approve")
    public ApiResponse<HandoverResponse> approveHandover(
            @PathVariable String handoverId, @Valid @RequestBody ApproveHandoverRequest body,
            HttpServletRequest request
    ) {
        return ok("CRM handover approved", service.approveHandover(UserAccessContext.from(request), handoverId, body), request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
