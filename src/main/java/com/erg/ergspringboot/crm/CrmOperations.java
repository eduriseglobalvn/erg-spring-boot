package com.erg.ergspringboot.crm;

import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolSummaryResponse;
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
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.crm.CrmSchoolService;
import com.erg.ergspringboot.service.crm.CrmWorkspaceService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Public CRM application boundary. The facade keeps controllers independent of
 * repositories/entities while the existing services are migrated inward.
 */
@Service
public class CrmOperations {

    private final CrmWorkspaceService workspace;
    private final CrmSchoolService schoolReads;

    public CrmOperations(CrmWorkspaceService workspace, CrmSchoolService schoolReads) {
        this.workspace = workspace;
        this.schoolReads = schoolReads;
    }

    public WorkspaceResponse workspace(UserAccessContext access) {
        return workspace.workspace(access);
    }

    public LeadResponse updateLeadStatus(
            UserAccessContext access, String leadId, UpdateLeadStatusRequest request) {
        return workspace.updateLeadStatus(access, leadId, request);
    }

    public OpportunityResponse createOpportunity(
            UserAccessContext access, String idempotencyKey, CreateOpportunityRequest request) {
        return workspace.createOpportunity(access, idempotencyKey, request);
    }

    public OpportunityResponse updateOpportunity(
            UserAccessContext access, String opportunityId, UpdateOpportunityRequest request) {
        return workspace.updateOpportunity(access, opportunityId, request);
    }

    public FollowUpResponse createFollowUp(
            UserAccessContext access, String idempotencyKey, CreateFollowUpRequest request) {
        return workspace.createFollowUp(access, idempotencyKey, request);
    }

    public FollowUpResponse updateFollowUpCompletion(
            UserAccessContext access, String followUpId, UpdateFollowUpCompletionRequest request) {
        return workspace.updateFollowUpCompletion(access, followUpId, request);
    }

    public HandoverResponse createHandover(
            UserAccessContext access, String idempotencyKey, CreateHandoverRequest request) {
        return workspace.createHandover(access, idempotencyKey, request);
    }

    public HandoverResponse approveHandover(
            UserAccessContext access, String handoverId, ApproveHandoverRequest request) {
        return workspace.approveHandover(access, handoverId, request);
    }

    public List<CrmSchoolSummaryResponse> signedSchoolsBySeo(String seoUserId) {
        return schoolReads.signedSchoolsBySeo(seoUserId);
    }

    public CrmSchoolPerformanceResponse performance(
            String seoUserId, OffsetDateTime from, OffsetDateTime to) {
        return schoolReads.performance(seoUserId, from, to);
    }
}
