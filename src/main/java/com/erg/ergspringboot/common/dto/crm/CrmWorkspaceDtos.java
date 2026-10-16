package com.erg.ergspringboot.common.dto.crm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class CrmWorkspaceDtos {

    private CrmWorkspaceDtos() {
    }

    public record WorkspaceResponse(
            List<LeadResponse> leads,
            List<OpportunityResponse> opportunities,
            List<FollowUpResponse> followUps,
            List<HandoverResponse> handovers
    ) {
    }

    public record LeadResponse(
            String id,
            String schoolId,
            String schoolName,
            String district,
            String province,
            String segment,
            String contactName,
            String contactRole,
            String phone,
            String source,
            String ownerName,
            String status,
            int probability,
            int expectedStudents,
            int expectedClasses,
            LocalDate nextFollowUpAt,
            OffsetDateTime lastTouchAt,
            String notes,
            List<String> risks,
            long version
    ) {
        public static LeadResponse empty(String id, String schoolId, String status, long version) {
            return new LeadResponse(id, schoolId, null, null, null, null, null, null, null, null, null,
                    status, 0, 0, 0, null, null, null, List.of(), version);
        }
    }

    public record UpdateLeadStatusRequest(
            @NotBlank String status,
            @NotNull @Min(0) Long expectedVersion
    ) {
    }

    public record RevenueLine(
            @NotBlank String id,
            @NotBlank String program,
            @NotBlank String packageName,
            @Min(0) int classCount,
            @Min(0) int studentsPerClass,
            @DecimalMin("0") BigDecimal pricePerStudent,
            @DecimalMin("0") @DecimalMax("1") BigDecimal discountRate,
            @Min(0) int durationMonths
    ) {
    }

    public record CostLine(
            @NotBlank String id,
            @NotBlank String category,
            @NotBlank String description,
            @Min(0) int quantity,
            @DecimalMin("0") BigDecimal unitCost,
            @Min(0) int durationMonths,
            boolean requiredForHandover
    ) {
    }

    public record CreateOpportunityRequest(
            @NotBlank String leadId,
            @NotBlank @Size(max = 100) String code,
            @NotBlank String stage,
            @NotBlank String academicYear,
            @NotBlank String contractTerm,
            @NotBlank String startMonth,
            @NotNull List<@Valid RevenueLine> revenueLines,
            @NotNull List<@Valid CostLine> costLines
    ) {
    }

    public record UpdateOpportunityRequest(
            @NotBlank String stage,
            @NotBlank String academicYear,
            @NotBlank String contractTerm,
            @NotBlank String startMonth,
            @NotNull List<@Valid RevenueLine> revenueLines,
            @NotNull List<@Valid CostLine> costLines,
            @NotNull @Min(0) Long expectedVersion
    ) {
    }

    public record OpportunityResponse(
            String id,
            String leadId,
            String code,
            String stage,
            String academicYear,
            String contractTerm,
            String startMonth,
            String ownerName,
            List<RevenueLine> revenueLines,
            List<CostLine> costLines,
            long version
    ) {
    }

    public record CreateFollowUpRequest(
            @NotBlank String leadId,
            String opportunityId,
            @NotBlank String type,
            @NotBlank @Size(max = 500) String title,
            @NotNull LocalDate dueAt
    ) {
    }

    public record UpdateFollowUpCompletionRequest(
            boolean done,
            @NotNull @Min(0) Long expectedVersion
    ) {
    }

    public record FollowUpResponse(
            String id,
            String leadId,
            String type,
            String title,
            LocalDate dueAt,
            String ownerName,
            boolean done,
            long version
    ) {
    }

    public record HandoverChecklistItem(
            @NotBlank String id,
            @NotBlank String label,
            boolean done
    ) {
    }

    public record CreateHandoverRequest(
            @NotBlank String leadId,
            @NotBlank String opportunityId,
            @NotNull List<@Valid HandoverChecklistItem> checklist
    ) {
    }

    public record ApproveHandoverRequest(@NotNull @Min(0) Long expectedVersion) {
    }

    public record HandoverResponse(
            String id,
            String leadId,
            String opportunityId,
            String requestedBy,
            OffsetDateTime requestedAt,
            String status,
            List<HandoverChecklistItem> checklist,
            long version
    ) {
    }
}
