package com.erg.ergspringboot.service.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolSummaryResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.crm.CrmSchoolContact;
import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.crm.CrmSchoolContactRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolDealRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CrmSchoolServiceTest {

    @Mock
    private EducationUnitRepository educationUnitRepository;
    @Mock
    private CrmSchoolProfileRepository profileRepository;
    @Mock
    private CrmSchoolDealRepository dealRepository;
    @Mock
    private CrmSchoolContactRepository contactRepository;
    @Mock
    private AuthorizationOperations authorization;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsSignedSchoolsForSeoWithPrimaryContactAndReferrer() {
        CrmSchoolService service = service();
        EducationUnit school = school("sch_binh_an", "THCS Binh An", "signed");
        CrmSchoolDeal deal = deal("deal_1", "sch_binh_an", "seo_1", "signed", new BigDecimal("50000000"));
        CrmSchoolProfile profile = profile("sch_binh_an", "ref_1", "Nguyen Gioi Thieu", "0901000000");
        CrmSchoolContact contact = contact("sch_binh_an", "principal", "Tran Hieu Truong", "0902000000", true);

        when(dealRepository.findByTenantIdAndOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc("tenant-a", "seo_1", "signed"))
                .thenReturn(List.of(deal));
        when(educationUnitRepository.findByTenantIdAndIdInOrderByNameAsc(eq("tenant-a"), eq(List.of("sch_binh_an")), any(Pageable.class)))
                .thenReturn(List.of(school));
        when(profileRepository.findByTenantIdAndSchoolIdIn("tenant-a", List.of("sch_binh_an"))).thenReturn(List.of(profile));
        when(contactRepository.findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc("tenant-a", List.of("sch_binh_an")))
                .thenReturn(List.of(contact));

        List<CrmSchoolSummaryResponse> response = service.schoolsBySeo(access("seo_1", List.of("*")), "seo_1", true);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().schoolId()).isEqualTo("sch_binh_an");
        assertThat(response.getFirst().signedRevenue()).isEqualByComparingTo("50000000");
        assertThat(response.getFirst().primaryContact().position()).isEqualTo("principal");
        assertThat(response.getFirst().introducedBy().name()).isEqualTo("Nguyen Gioi Thieu");
    }

    @Test
    void seoStaffCannotReadAnotherSeoPipelineWithoutAnalyticsPermission() {
        CrmSchoolService service = service();
        authenticate("seo_1");
        when(authorization.isAllowed(any(), eq("crm.analytics.read"), eq(null), eq("tenant-a"))).thenReturn(false);

        assertThatThrownBy(() -> service.schoolsBySeo(access("seo_1", List.of("*")), "seo_2", true))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dealRepository);
    }

    @Test
    void analyticsReaderSeesOnlySchoolsInsideTenantAndManagedScope() {
        CrmSchoolService service = service();
        authenticate("lead_1");
        when(authorization.isAllowed(any(), eq("crm.analytics.read"), eq(null), eq("tenant-a"))).thenReturn(true);
        when(dealRepository.findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc("tenant-a", "seo_2")).thenReturn(List.of(
                deal("deal_1", "school-in-scope", "seo_2", "proposal", BigDecimal.ZERO),
                deal("deal_2", "school-out-of-scope", "seo_2", "signed", BigDecimal.TEN)));
        when(educationUnitRepository.findByTenantIdAndIdInOrderByNameAsc(eq("tenant-a"), eq(List.of("school-in-scope")), any(Pageable.class)))
                .thenReturn(List.of(school("school-in-scope", "In scope", "active")));
        when(profileRepository.findByTenantIdAndSchoolIdIn("tenant-a", List.of("school-in-scope"))).thenReturn(List.of());
        when(contactRepository.findByTenantIdAndSchoolIdInOrderByIsPrimaryDescCreatedAtAsc("tenant-a", List.of("school-in-scope")))
                .thenReturn(List.of());

        List<CrmSchoolSummaryResponse> response =
                service.schoolsBySeo(access("lead_1", List.of("school-in-scope")), "seo_2", false);

        assertThat(response).extracting(CrmSchoolSummaryResponse::schoolId).containsExactly("school-in-scope");
        verify(dealRepository, never()).findByTenantIdAndOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc(any(), any(), any());
    }

    @Test
    void calculatesSeoConversionAndRevenueFromDealsInScope() {
        CrmSchoolService service = service();
        OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        when(dealRepository.findByTenantIdAndOwnerUserIdAndCreatedAtBetween("tenant-a", "seo_1", from, to)).thenReturn(List.of(
                deal("deal_1", "sch_1", "seo_1", "signed", new BigDecimal("50000000")),
                deal("deal_2", "sch_2", "seo_1", "lost", BigDecimal.ZERO),
                deal("deal_3", "sch_3", "seo_1", "negotiating", BigDecimal.ZERO),
                deal("deal_4", "sch_4", "seo_1", "signed", new BigDecimal("70000000")),
                deal("deal_5", "sch_foreign", "seo_1", "signed", new BigDecimal("90000000"))
        ));

        CrmSchoolPerformanceResponse response = service.performance(
                access("seo_1", List.of("sch_1", "sch_2", "sch_3", "sch_4")), "seo_1", from, to);

        assertThat(response.totalSchools()).isEqualTo(4);
        assertThat(response.signedSchools()).isEqualTo(2);
        assertThat(response.lostSchools()).isEqualTo(1);
        assertThat(response.inProgressSchools()).isEqualTo(1);
        assertThat(response.conversionRate()).isEqualByComparingTo("0.50");
        assertThat(response.signedRevenue()).isEqualByComparingTo("120000000");
        assertThat(response.averageDealValue()).isEqualByComparingTo("60000000");
    }

    private CrmSchoolService service() {
        return new CrmSchoolService(educationUnitRepository, profileRepository, dealRepository, contactRepository, authorization);
    }

    private UserAccessContext access(String userId, List<String> units) {
        return new UserAccessContext(userId, "tenant-a", units, List.of(), null, null);
    }

    private void authenticate(String userId) {
        ErgPrincipal principal = ErgPrincipal.authenticated(userId, "session", "tenant-a", List.of(), List.of(),
                List.of(), List.of(), "staff", null, Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private EducationUnit school(String id, String name, String status) {
        EducationUnit educationUnit = new EducationUnit();
        educationUnit.setId(id);
        educationUnit.setName(name);
        educationUnit.setType("school");
        educationUnit.setStatus(status);
        return educationUnit;
    }

    private CrmSchoolDeal deal(String id, String schoolId, String ownerUserId, String stage, BigDecimal revenue) {
        CrmSchoolDeal deal = new CrmSchoolDeal();
        deal.setId(id);
        deal.setTenantId("tenant-a");
        deal.setSchoolId(schoolId);
        deal.setOwnerUserId(ownerUserId);
        deal.setStage(stage);
        deal.setSignedRevenue(revenue);
        deal.setCreatedAt(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        deal.setSignedAt("signed".equals(stage) ? OffsetDateTime.parse("2026-03-01T00:00:00Z") : null);
        return deal;
    }

    private CrmSchoolProfile profile(String schoolId, String referrerUserId, String referrerName, String referrerPhone) {
        CrmSchoolProfile profile = new CrmSchoolProfile();
        profile.setSchoolId(schoolId);
        profile.setIntroducedByUserId(referrerUserId);
        profile.setIntroducedByName(referrerName);
        profile.setIntroducedByPhone(referrerPhone);
        return profile;
    }

    private CrmSchoolContact contact(String schoolId, String position, String name, String phone, boolean primary) {
        CrmSchoolContact contact = new CrmSchoolContact();
        contact.setSchoolId(schoolId);
        contact.setPosition(position);
        contact.setFullName(name);
        contact.setPhone(phone);
        contact.setIsPrimary(primary);
        return contact;
    }
}
