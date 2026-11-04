package com.erg.ergspringboot.crm.internal;

import com.erg.ergspringboot.common.dto.crm.CrmWorkspaceDtos.HandoverChecklistItem;
import com.erg.ergspringboot.crm.persistence.CrmSchoolActivity;
import com.erg.ergspringboot.crm.persistence.CrmSchoolActivityRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Handover checklist readiness, with item completion verified against server-held follow-ups. */
final class CrmHandoverChecklistVerifier {

    private final CrmSchoolActivityRepository activityRepository;

    CrmHandoverChecklistVerifier(CrmSchoolActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    static boolean isReady(List<HandoverChecklistItem> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return false;
        }
        return checklist.stream().allMatch(HandoverChecklistItem::done);
    }

    /**
     * Client-declared {@code done} flags are not trusted where the server holds the evidence:
     * a checklist item whose id names a follow-up of the same school takes that follow-up's
     * completion state.
     */
    List<HandoverChecklistItem> verifiedChecklist(
            String tenantId, String schoolId, List<HandoverChecklistItem> checklist
    ) {
        List<HandoverChecklistItem> items = CrmWorkspaceMapper.safe(checklist);
        List<String> ids = items.stream()
                .map(HandoverChecklistItem::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (ids.isEmpty()) {
            return items;
        }
        Map<String, Boolean> followUps = activityRepository.findByTenantIdAndSchoolIdAndIdIn(tenantId, schoolId, ids)
                .stream()
                .collect(Collectors.toMap(CrmSchoolActivity::getId,
                        activity -> Boolean.TRUE.equals(activity.getDone()), (left, right) -> left));
        return items.stream()
                .map(item -> followUps.containsKey(item.id())
                        ? new HandoverChecklistItem(item.id(), item.label(), followUps.get(item.id()))
                        : item)
                .toList();
    }
}
