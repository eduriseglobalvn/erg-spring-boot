package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.EvidenceView;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestStepView;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestEvidence;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestEvidenceRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStep;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Request views with their steps (approver names) and evidence, loaded in bulk for a page. */
@Component
public class RequestViewAssembler {

    static final String SCHEDULE = "SCHEDULE";
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TkRequestStepRepository steps;
    private final TkRequestEvidenceRepository evidence;
    private final ApprovalChainResolver chains;

    RequestViewAssembler(TkRequestStepRepository steps, TkRequestEvidenceRepository evidence,
            ApprovalChainResolver chains) {
        this.steps = steps;
        this.evidence = evidence;
        this.chains = chains;
    }

    public RequestView one(TkRequest r) {
        return many(r.getTenantId(), List.of(r)).get(0);
    }

    public List<RequestView> many(String tenantId, List<TkRequest> requests) {
        if (requests.isEmpty()) return List.of();
        List<String> ids = requests.stream().map(TkRequest::getId).toList();
        Map<String, List<TkRequestStep>> stepsByRequest = steps
                .findByTenantIdAndRequestIdInOrderByStepNoAscApproverIdAsc(tenantId, ids).stream()
                .collect(Collectors.groupingBy(TkRequestStep::getRequestId));
        Map<String, List<TkRequestEvidence>> evidenceByRequest = evidence
                .findByTenantIdAndRequestIdInOrderByRefAsc(tenantId, ids).stream()
                .collect(Collectors.groupingBy(TkRequestEvidence::getRequestId));
        Map<String, String> names = chains.names(tenantId, stepsByRequest.values().stream().flatMap(List::stream)
                .flatMap(s -> Stream.of(s.getApproverId(), s.getActedBy())).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return requests.stream().map(r -> view(r,
                stepsByRequest.getOrDefault(r.getId(), List.of()).stream().map(s -> step(s, names)).toList(),
                evidenceByRequest.getOrDefault(r.getId(), List.of()).stream()
                        .map(e -> new EvidenceView(e.getKind(), e.getRef(), e.getLabel())).toList())).toList();
    }

    static EvidenceView evidence(String ref, String label) {
        return new EvidenceView(SCHEDULE, ref, label);
    }

    static String ref(TeacherPeriod p) {
        return p.start().toString();
    }

    /** "16/11 08:00–08:45 · 7A · Toán". */
    static String label(TeacherPeriod p) {
        String text = p.start().atZone(WorkContextResolver.ZONE).format(DAY_TIME) + "–"
                + p.end().atZone(WorkContextResolver.ZONE).format(TIME)
                + (p.className() == null ? "" : " · " + p.className())
                + (p.subject() == null ? "" : " · " + p.subject());
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private static RequestStepView step(TkRequestStep s, Map<String, String> names) {
        return new RequestStepView(s.getStepNo(), s.getResolver(), s.getApproverId(),
                s.getApproverId() == null ? null : names.get(s.getApproverId()), s.getStatus(), s.getActedBy(),
                s.getActedAt(), Boolean.TRUE.equals(s.getOnBehalf()), s.getComment(), s.getViewedAt(),
                s.getActivatedAt(), s.getDueAt());
    }

    private static RequestView view(TkRequest r, List<RequestStepView> stepViews, List<EvidenceView> evidenceViews) {
        return new RequestView(r.getId(), r.getCode(), r.getType(), r.getLeaveKind(), r.getFromDate(), r.getToDate(),
                r.getPart(), r.getDays(), r.getWorkDate(), r.getProposedOut(), r.getExplanationReason(), r.getNote(),
                r.getStatus(), r.getCurrentStep(), r.getSubmittedAt(), r.getLastRemindedAt(), stepViews, evidenceViews);
    }
}
