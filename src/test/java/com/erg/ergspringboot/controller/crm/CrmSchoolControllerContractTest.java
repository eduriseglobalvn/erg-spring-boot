package com.erg.ergspringboot.controller.crm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolPerformanceResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.CrmSchoolSummaryResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.SchoolContactResponse;
import com.erg.ergspringboot.common.dto.crm.CrmSchoolDtos.SchoolReferrerResponse;
import com.erg.ergspringboot.exception.GlobalExceptionHandler;
import com.erg.ergspringboot.crm.CrmOperations;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CrmSchoolControllerContractTest {

    @Test
    void listsSignedSchoolsForSeo() throws Exception {
        CrmOperations service = mock(CrmOperations.class);
        when(service.signedSchoolsBySeo("seo_1")).thenReturn(List.of(new CrmSchoolSummaryResponse(
                "sch_binh_an",
                "THCS Binh An",
                "signed",
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                new BigDecimal("50000000"),
                new SchoolContactResponse("contact_1", "Tran Hieu Truong", "principal", "0902000000", null, true),
                new SchoolReferrerResponse("ref_1", "Nguyen Gioi Thieu", "0901000000", null)
        )));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/v1/crm/seo/seo_1/schools").param("signed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].schoolId").value("sch_binh_an"))
                .andExpect(jsonPath("$.data.items[0].primaryContact.name").value("Tran Hieu Truong"))
                .andExpect(jsonPath("$.data.items[0].introducedBy.name").value("Nguyen Gioi Thieu"));
    }

    @Test
    void returnsSeoPerformance() throws Exception {
        CrmOperations service = mock(CrmOperations.class);
        OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        when(service.performance("seo_1", from, to)).thenReturn(new CrmSchoolPerformanceResponse(
                4,
                2,
                1,
                1,
                new BigDecimal("0.50"),
                new BigDecimal("120000000"),
                new BigDecimal("60000000")
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/v1/crm/seo/seo_1/performance")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-05-26T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedSchools").value(2))
                .andExpect(jsonPath("$.data.conversionRate").value(0.50))
                .andExpect(jsonPath("$.data.signedRevenue").value(120000000));
    }

    private MockMvc mockMvc(CrmOperations service) {
        return MockMvcBuilders.standaloneSetup(new CrmSchoolController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new RequestIdFilter())
                .build();
    }
}
