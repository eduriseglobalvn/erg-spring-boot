package com.erg.ergspringboot.service.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CreateOpportunityRequest;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.UpdateLeadStatusRequest;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.crm.CrmHandoverRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolActivityRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolContactRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolDealRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CrmWorkspaceServiceTest {

    @Mock EducationUnitRepository unitRepository;
    @Mock CrmSchoolProfileRepository profileRepository;
    @Mock CrmSchoolContactRepository contactRepository;
    @Mock CrmSchoolDealRepository dealRepository;
    @Mock CrmSchoolActivityRepository activityRepository;
    @Mock CrmHandoverRepository handoverRepository;

    @Test
    void workspaceOnlyIncludesSchoolsFromPrincipalTenantAndManagedScope() {
        CrmWorkspaceService service = service();
        EducationUnit allowed = unit("school-1", "tenant-a");
        EducationUnit outsideManagedScope = unit("school-2", "tenant-a");
        when(unitRepository.findByTenantIdOrderByNameAsc(org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(List.of(allowed, outsideManagedScope));
        when(profileRepository.findBySchoolIdIn(List.of("school-1"))).thenReturn(List.of(profile("lead-1", "school-1")));
        when(contactRepository.findBySchoolIdInOrderByIsPrimaryDescCreatedAtAsc(List.of("school-1"))).thenReturn(List.of());
        when(dealRepository.findBySchoolIdInOrderByCreatedAtDesc(List.of("school-1"))).thenReturn(List.of());
        when(activityRepository.findBySchoolIdInOrderByDueAtAsc(List.of("school-1"))).thenReturn(List.of());
        when(handoverRepository.findByTenantIdAndSchoolIdInOrderByCreatedAtDesc("tenant-a", List.of("school-1")))
                .thenReturn(List.of());

        var response = service.workspace(access("tenant-a", List.of("school-1")));

        assertThat(response.leads()).extracting("id").containsExactly("lead-1");
        verify(profileRepository).findBySchoolIdIn(List.of("school-1"));
    }

    @Test
    void rejectsLeadMutationOutsideTenantEvenWhenClientKnowsLeadId() {
        CrmWorkspaceService service = service();
        CrmSchoolProfile profile = profile("lead-1", "school-foreign");
        when(profileRepository.findById("lead-1")).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId("school-foreign", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLeadStatus(
                access("tenant-a", List.of("*")), "lead-1", new UpdateLeadStatusRequest("active", 0L)
        )).isInstanceOf(ForbiddenException.class);

        verify(profileRepository, never()).save(profile);
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
        when(profileRepository.findById("lead-1")).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId("school-1", "tenant-a")).thenReturn(Optional.of(unit("school-1", "tenant-a")));
        when(dealRepository.findByOwnerUserIdAndIdempotencyKey("seo-1", "create-opportunity-1"))
                .thenReturn(Optional.of(existing));

        var response = service.createOpportunity(
                access("tenant-a", List.of("school-1")), "create-opportunity-1",
                new CreateOpportunityRequest("lead-1", "CRM-001", "proposal", "2026-2027", "10 months", "2026-08", List.of(), List.of())
        );

        assertThat(response.id()).isEqualTo("opportunity-1");
        verify(dealRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private CrmWorkspaceService service() {
        return new CrmWorkspaceService(unitRepository, profileRepository, contactRepository, dealRepository,
                activityRepository, handoverRepository);
    }

    private UserAccessContext access(String tenant, List<String> schools) {
        return new UserAccessContext("seo-1", tenant, schools, List.of(), null, null);
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
