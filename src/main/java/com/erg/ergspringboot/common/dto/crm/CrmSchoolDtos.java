package com.erg.ergspringboot.common.dto.crm;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CrmSchoolDtos {

    private CrmSchoolDtos() {
    }

    public record CrmSchoolListResponse(List<CrmSchoolSummaryResponse> items) {
    }

    public record CrmSchoolSummaryResponse(
            String schoolId,
            String schoolName,
            String status,
            OffsetDateTime signedAt,
            BigDecimal signedRevenue,
            SchoolContactResponse primaryContact,
            SchoolReferrerResponse introducedBy
    ) {
    }

    public record SchoolContactResponse(
            String id,
            String name,
            String position,
            String phone,
            String email,
            boolean primary
    ) {
    }

    public record SchoolReferrerResponse(
            String userId,
            String name,
            String phone,
            String note
    ) {
    }

    public record CrmSchoolPerformanceResponse(
            long totalSchools,
            long signedSchools,
            long lostSchools,
            long inProgressSchools,
            BigDecimal conversionRate,
            BigDecimal signedRevenue,
            BigDecimal averageDealValue
    ) {
    }
}
