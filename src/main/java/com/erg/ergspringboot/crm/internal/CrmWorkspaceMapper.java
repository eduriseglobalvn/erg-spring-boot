package com.erg.ergspringboot.crm.internal;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.CostLine;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.FollowUpResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverChecklistItem;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.LeadResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.OpportunityResponse;
import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.RevenueLine;
import com.erg.ergspringboot.crm.persistence.CrmHandover;
import com.erg.ergspringboot.crm.persistence.CrmSchoolActivity;
import com.erg.ergspringboot.crm.persistence.CrmSchoolContact;
import com.erg.ergspringboot.crm.persistence.CrmSchoolDeal;
import com.erg.ergspringboot.crm.persistence.CrmSchoolProfile;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import java.math.BigDecimal;
import java.util.List;

/** Maps CRM entities to workspace DTOs and DTO lines to their persisted JSON rows. */
final class CrmWorkspaceMapper {

    private CrmWorkspaceMapper() {
    }

    static LeadResponse leadResponse(CrmSchoolProfile profile, EducationUnit unit, CrmSchoolContact contact) {
        return new LeadResponse(
                profile.getId(), profile.getSchoolId(), unit == null ? null : unit.getName(), profile.getDistrict(),
                profile.getProvince(), profile.getSegment(), contact == null ? null : contact.getFullName(),
                contact == null ? null : firstNonBlank(contact.getPosition(), contact.getContactType()),
                contact == null ? null : contact.getPhone(), profile.getSource(),
                firstNonBlank(profile.getLeadOwnerUserId(), profile.getOwnerUserId()), profile.getLeadStatus(),
                value(profile.getProbability()), value(profile.getExpectedStudents()), value(profile.getExpectedClasses()),
                profile.getNextFollowUpAt(), profile.getLastTouchAt(), profile.getNote(), safe(profile.getRisks()), profile.getVersion()
        );
    }

    static OpportunityResponse opportunityResponse(CrmSchoolDeal deal, CrmSchoolProfile profile) {
        return new OpportunityResponse(
                deal.getId(), profile == null ? null : profile.getId(), deal.getCode(), deal.getStage(),
                deal.getAcademicYear(), deal.getContractTerm(), deal.getStartMonth(), deal.getOwnerUserId(),
                revenueLines(deal.getRevenueLines()), costLines(deal.getCostLines()), deal.getVersion()
        );
    }

    static FollowUpResponse followUpResponse(CrmSchoolActivity activity, CrmSchoolProfile profile) {
        return new FollowUpResponse(
                activity.getId(), profile == null ? null : profile.getId(), activity.getType(),
                firstNonBlank(activity.getTitle(), activity.getContent()), activity.getDueAt(), activity.getActorUserId(),
                Boolean.TRUE.equals(activity.getDone()), activity.getVersion()
        );
    }

    static HandoverResponse handoverResponse(CrmHandover handover) {
        return new HandoverResponse(
                handover.getId(), handover.getLeadId(), handover.getOpportunityId(), handover.getRequestedBy(),
                handover.getCreatedAt(), handover.getStatus(), checklistItems(handover.getChecklist()), handover.getVersion()
        );
    }

    static CrmSchoolDeal.RevenueLine revenueRow(RevenueLine line) {
        return new CrmSchoolDeal.RevenueLine(line.id(), line.program(), line.packageName(), line.classCount(),
                line.studentsPerClass(), line.pricePerStudent(), line.discountRate(), line.durationMonths());
    }

    static CrmSchoolDeal.CostLine costRow(CostLine line) {
        return new CrmSchoolDeal.CostLine(line.id(), line.category(), line.description(), line.quantity(),
                line.unitCost(), line.durationMonths(), line.requiredForHandover());
    }

    static CrmSchoolDeal.ChecklistItem checklistRow(HandoverChecklistItem item) {
        return new CrmSchoolDeal.ChecklistItem(item.id(), item.label(), item.done());
    }

    static List<RevenueLine> revenueLines(List<CrmSchoolDeal.RevenueLine> rows) {
        return safe(rows).stream().map(row -> new RevenueLine(
                text(row.id()), text(row.program()), text(row.packageName()), value(row.classCount()),
                value(row.studentsPerClass()), amount(row.pricePerStudent()), amount(row.discountRate()),
                value(row.durationMonths()))).toList();
    }

    static List<CostLine> costLines(List<CrmSchoolDeal.CostLine> rows) {
        return safe(rows).stream().map(row -> new CostLine(
                text(row.id()), text(row.category()), text(row.description()), value(row.quantity()),
                amount(row.unitCost()), value(row.durationMonths()), Boolean.TRUE.equals(row.requiredForHandover())))
                .toList();
    }

    static List<HandoverChecklistItem> checklistItems(List<CrmSchoolDeal.ChecklistItem> rows) {
        return safe(rows).stream()
                .map(row -> new HandoverChecklistItem(text(row.id()), text(row.label()), Boolean.TRUE.equals(row.done())))
                .toList();
    }

    static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    static int value(Integer value) {
        return value == null ? 0 : value;
    }

    static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
