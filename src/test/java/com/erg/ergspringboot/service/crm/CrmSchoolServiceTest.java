package com.erg.ergspringboot.service.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolSummaryResponse;
import com.erg.ergspringboot.model.crm.CrmSchoolContact;
import com.erg.ergspringboot.model.crm.CrmSchoolDeal;
import com.erg.ergspringboot.model.crm.CrmSchoolProfile;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.repository.crm.CrmSchoolContactRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolDealRepository;
import com.erg.ergspringboot.repository.crm.CrmSchoolProfileRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void listsSignedSchoolsForSeoWithPrimaryContactAndReferrer() {
        CrmSchoolService service = new CrmSchoolService(educationUnitRepository, profileRepository, dealRepository, contactRepository);
        EducationUnit school = school("sch_binh_an", "THCS Binh An", "signed");
        CrmSchoolDeal deal = deal("deal_1", "sch_binh_an", "seo_1", "signed", new BigDecimal("50000000"));
        CrmSchoolProfile profile = profile("sch_binh_an", "ref_1", "Nguyen Gioi Thieu", "0901000000");
        CrmSchoolContact contact = contact("sch_binh_an", "principal", "Tran Hieu Truong", "0902000000", true);

        when(dealRepository.findByOwnerUserIdAndStageIgnoreCaseOrderBySignedAtDesc("seo_1", "signed"))
                .thenReturn(List.of(deal));
        when(educationUnitRepository.findByIdInOrderByNameAsc(List.of("sch_binh_an"))).thenReturn(List.of(school));
        when(profileRepository.findBySchoolIdIn(List.of("sch_binh_an"))).thenReturn(List.of(profile));
        when(contactRepository.findBySchoolIdInOrderByIsPrimaryDescCreatedAtAsc(List.of("sch_binh_an"))).thenReturn(List.of(contact));

        List<CrmSchoolSummaryResponse> response = service.signedSchoolsBySeo("seo_1");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().schoolId()).isEqualTo("sch_binh_an");
        assertThat(response.getFirst().signedRevenue()).isEqualByComparingTo("50000000");
        assertThat(response.getFirst().primaryContact().position()).isEqualTo("principal");
        assertThat(response.getFirst().introducedBy().name()).isEqualTo("Nguyen Gioi Thieu");
    }

    @Test
    void calculatesSeoConversionAndRevenueFromDeals() {
        CrmSchoolService service = new CrmSchoolService(educationUnitRepository, profileRepository, dealRepository, contactRepository);
        OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        when(dealRepository.findByOwnerUserIdAndCreatedAtBetween("seo_1", from, to)).thenReturn(List.of(
                deal("deal_1", "sch_1", "seo_1", "signed", new BigDecimal("50000000")),
                deal("deal_2", "sch_2", "seo_1", "lost", BigDecimal.ZERO),
                deal("deal_3", "sch_3", "seo_1", "negotiating", BigDecimal.ZERO),
                deal("deal_4", "sch_4", "seo_1", "signed", new BigDecimal("70000000"))
        ));

        CrmSchoolPerformanceResponse response = service.performance("seo_1", from, to);

        assertThat(response.totalSchools()).isEqualTo(4);
        assertThat(response.signedSchools()).isEqualTo(2);
        assertThat(response.lostSchools()).isEqualTo(1);
        assertThat(response.inProgressSchools()).isEqualTo(1);
        assertThat(response.conversionRate()).isEqualByComparingTo("0.50");
        assertThat(response.signedRevenue()).isEqualByComparingTo("120000000");
        assertThat(response.averageDealValue()).isEqualByComparingTo("60000000");
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
