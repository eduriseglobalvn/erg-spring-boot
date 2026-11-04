package com.erg.ergspringboot.crm.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.ApproveHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateHandoverRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverChecklistItem;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateLeadStatusRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.crm.CrmHandover;
import com.erg.ergspringboot.model.crm.CrmSchoolActivity;
import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.crm.CrmHandoverRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolActivityRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolContactRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolDealRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CrmWorkspaceServiceTest {

    @Mock EducationUnitRepository unitRepository;
    @Mock CrmSchoolProfileRepository profileRepository;
    @Mock CrmSchoolContactRepository contactRepository;
    @Mock CrmSchoolDealRepository dealRepository;
    @Mock CrmSchoolActivityRepository activityRepository;
    @Mock CrmHandoverRepository handoverRepository;
    @Mock AuthorizationOperations authorization;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void workspaceOnlyIncludesSchoolsFromPrincipalTenantAndManagedScope() {
        CrmWorkspaceService service = service();
        EducationUnit allowed = unit("school-1", "tenant-a");
        when(unitRepository.findPageByTenantIdAndIdInOrderByNameAsc(eq("tenant-a"), eq(List.of("school-1")), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(allowed)));
        when(profileRepository.findByTenantIdAndSchoolIdIn("tenant-a", List.of("school-1")))
                .thenReturn(List.of(profile("lead-1", "school-1")));
        when(contactRepository.findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc("tenant-a", List.of("school-1")))
                .thenReturn(List.of());
        when(dealRepository.findByTenantIdAndSchoolIdInOrderByCreatedAtDesc("tenant-a", List.of("school-1"))).thenReturn(List.of());
        when(activityRepository.findByTenantIdAndSchoolIdInOrderByDueAtAsc("tenant-a", List.of("school-1"))).thenReturn(List.of());
        when(handoverRepository.findByTenantIdAndSchoolIdInOrderByCreatedAtDesc("tenant-a", List.of("school-1")))
                .thenReturn(List.of());

        var response = service.workspace(access("seo-1", "tenant-a", List.of("school-1")));

        assertThat(response.leads()).extracting("id").containsExactly("lead-1");
        assertThat(response.page().hasMore()).isFalse();
        assertThat(response.page().totalSchools()).isEqualTo(1);
        verify(profileRepository).findByTenantIdAndSchoolIdIn("tenant-a", List.of("school-1"));
    }

    @Test
    void rejectsLeadMutationOutsideTenantEvenWhenClientKnowsLeadId() {
        CrmWorkspaceService service = service();
        when(profileRepository.findByIdAndTenantId("lead-1", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLeadStatus(
                access("seo-1", "tenant-a", List.of("*")), "lead-1", new UpdateLeadStatusRequest("active", 0L)
        )).isInstanceOf(ResourceNotFoundException.class);

        verify(profileRepository, never()).save(any());
    }

    @Test
    void createOpportunityReplaysSameOwnerIdempotencyKey() {
        CrmWorkspaceService service = service();
        CrmSchoolProfile profile = profile("lead-1", "school-1");
        CrmSchoolDeal existing = new CrmSchoolDeal();
        existing.setId("opportunity-1");
        existing.setSchoolId("school-1");
        existing.setOwnerUserId("seo-1");
        existing.setStage("proposal");
        existing.setIdempotencyKey("create-opportunity-1");
        when(profileRepository.findByIdAndTenantId("lead-1", "tenant-a")).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId("school-1", "tenant-a")).thenReturn(Optional.of(unit("school-1", "tenant-a")));
        when(dealRepository.findByTenantIdAndOwnerUserIdAndIdempotencyKey("tenant-a", "seo-1", "create-opportunity-1"))
                .thenReturn(Optional.of(existing));

        var response = service.createOpportunity(
                access("seo-1", "tenant-a", List.of("school-1")), "create-opportunity-1",
                new CreateOpportunityRequest("lead-1", "CRM-001", "proposal", "2026-2027", "10 months", "2026-08", List.of(), List.of())
        );

        assertThat(response.id()).isEqualTo("opportunity-1");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void handoverWithOneUnfinishedChecklistItemIsDraftNotReady() {
        CrmWorkspaceService service = service();
        stubLeadAndDeal();
        when(handoverRepository.save(any(CrmHandover.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createHandover(access("seo-1", "tenant-a", List.of("school-1")), "handover-1",
                new CreateHandoverRequest("lead-1", "deal-1", List.of(
                        new HandoverChecklistItem("contract", "Contract signed", true),
                        new HandoverChecklistItem("kickoff", "Kick-off", false))));

        assertThat(response.status()).isEqualTo("draft");
    }

    @Test
    void handoverChecklistDoneFlagIsDerivedFromFollowUpWhenServerHasIt() {
        CrmWorkspaceService service = service();
        stubLeadAndDeal();
        CrmSchoolActivity followUp = new CrmSchoolActivity();
        followUp.setId("follow-up-1");
        followUp.setDone(false);
        when(activityRepository.findByTenantIdAndSchoolIdAndIdIn("tenant-a", "school-1", List.of("follow-up-1")))
                .thenReturn(List.of(followUp));
        when(handoverRepository.save(any(CrmHandover.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createHandover(access("seo-1", "tenant-a", List.of("school-1")), "handover-1",
                new CreateHandoverRequest("lead-1", "deal-1", List.of(
                        new HandoverChecklistItem("follow-up-1", "Contract meeting", true))));

        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.checklist()).singleElement().extracting(HandoverChecklistItem::done).isEqualTo(false);
    }

    @Test
    void requesterCannotApproveOwnHandover() {
        CrmWorkspaceService service = service();
        stubHandover(handover("seo-1", "ready"));
        authenticate("seo-1", true);

        assertThatThrownBy(() -> service.approveHandover(
                access("seo-1", "tenant-a", List.of("school-1")), "handover-1", new ApproveHandoverRequest(0L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own handover");
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void approvalRequiresDedicatedHandoverPermission() {
        CrmWorkspaceService service = service();
        stubHandover(handover("seo-1", "ready"));
        authenticate("manager-1", false);

        assertThatThrownBy(() -> service.approveHandover(
                access("manager-1", "tenant-a", List.of("school-1")), "handover-1", new ApproveHandoverRequest(0L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("crm.handover.approve");
    }

    @Test
    void onlyReadyHandoverCanBeApproved() {
        CrmWorkspaceService service = service();
        stubHandover(handover("seo-1", "approved"));
        authenticate("manager-1", true);

        assertThatThrownBy(() -> service.approveHandover(
                access("manager-1", "tenant-a", List.of("school-1")), "handover-1", new ApproveHandoverRequest(0L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvalRejectsChecklistWithAnyUnfinishedItem() {
        CrmWorkspaceService service = service();
        CrmHandover handover = handover("seo-1", "ready");
        handover.setChecklist(new ArrayList<>(List.of(
                new CrmSchoolDeal.ChecklistItem("a", "A", true),
                new CrmSchoolDeal.ChecklistItem("b", "B", false))));
        stubHandover(handover);
        authenticate("manager-1", true);

        assertThatThrownBy(() -> service.approveHandover(
                access("manager-1", "tenant-a", List.of("school-1")), "handover-1", new ApproveHandoverRequest(0L)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void anotherUserWithPermissionApprovesReadyHandover() {
        CrmWorkspaceService service = service();
        stubHandover(handover("seo-1", "ready"));
        authenticate("manager-1", true);
        when(profileRepository.findByIdAndTenantId("lead-1", "tenant-a")).thenReturn(Optional.of(profile("lead-1", "school-1")));
        CrmSchoolDeal deal = new CrmSchoolDeal();
        deal.setId("deal-1");
        deal.setSchoolId("school-1");
        when(dealRepository.findByIdAndTenantId("deal-1", "tenant-a")).thenReturn(Optional.of(deal));
        when(handoverRepository.save(any(CrmHandover.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveHandover(
                access("manager-1", "tenant-a", List.of("school-1")), "handover-1", new ApproveHandoverRequest(0L));

        assertThat(response.status()).isEqualTo("approved");
        assertThat(deal.getStage()).isEqualTo("active");
    }

    private void stubLeadAndDeal() {
        when(profileRepository.findByIdAndTenantId("lead-1", "tenant-a")).thenReturn(Optional.of(profile("lead-1", "school-1")));
        when(unitRepository.findByIdAndTenantId("school-1", "tenant-a")).thenReturn(Optional.of(unit("school-1", "tenant-a")));
        CrmSchoolDeal deal = new CrmSchoolDeal();
        deal.setId("deal-1");
        deal.setSchoolId("school-1");
        when(dealRepository.findByIdAndTenantId("deal-1", "tenant-a")).thenReturn(Optional.of(deal));
    }

    private void stubHandover(CrmHandover handover) {
        when(handoverRepository.findByIdAndTenantId("handover-1", "tenant-a")).thenReturn(Optional.of(handover));
        when(unitRepository.findByIdAndTenantId("school-1", "tenant-a")).thenReturn(Optional.of(unit("school-1", "tenant-a")));
    }

    private CrmHandover handover(String requestedBy, String status) {
        CrmHandover handover = new CrmHandover();
        handover.setId("handover-1");
        handover.setTenantId("tenant-a");
        handover.setSchoolId("school-1");
        handover.setLeadId("lead-1");
        handover.setOpportunityId("deal-1");
        handover.setRequestedBy(requestedBy);
        handover.setStatus(status);
        handover.setChecklist(new ArrayList<>(List.of(new CrmSchoolDeal.ChecklistItem("contract", "Contract", true))));
        return handover;
    }

    private void authenticate(String userId, boolean canApprove) {
        ErgPrincipal principal = ErgPrincipal.authenticated(userId, "session", "tenant-a", List.of(), List.of(),
                List.of(), List.of(), "staff", null, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        lenient().when(authorization.isAllowed(any(), eq("crm.handover.approve"), eq(null), eq("tenant-a")))
                .thenReturn(canApprove);
    }

    private CrmWorkspaceService service() {
        return new CrmWorkspaceService(unitRepository, profileRepository, contactRepository, dealRepository,
                activityRepository, handoverRepository, authorization);
    }

    private UserAccessContext access(String userId, String tenant, List<String> schools) {
        return new UserAccessContext(userId, tenant, schools, List.of(), null, null);
    }

    private EducationUnit unit(String id, String tenant) {
        EducationUnit unit = new EducationUnit();
        unit.setId(id);
        unit.setTenantId(tenant);
        unit.setName(id);
        unit.setStatus("active");
        return unit;
    }

    private CrmSchoolProfile profile(String id, String schoolId) {
        CrmSchoolProfile profile = new CrmSchoolProfile();
        profile.setId(id);
        profile.setSchoolId(schoolId);
        profile.setLeadStatus("new_lead");
        return profile;
    }
}
