package com.erg.ergspringboot.model.crm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Typed jsonb rows for CRM deals and handovers. Components are boxed and unknown keys ignored so rows written
 * by older releases (missing or extra keys) still load; the service applies defaults when mapping to DTOs.
 */
public final class CrmDealLines {

    private CrmDealLines() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RevenueLine(
            String id,
            String program,
            String packageName,
            Integer classCount,
            Integer studentsPerClass,
            BigDecimal pricePerStudent,
            BigDecimal discountRate,
            Integer durationMonths
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostLine(
            String id,
            String category,
            String description,
            Integer quantity,
            BigDecimal unitCost,
            Integer durationMonths,
            Boolean requiredForHandover
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChecklistItem(String id, String label, Boolean done) {
    }
}
