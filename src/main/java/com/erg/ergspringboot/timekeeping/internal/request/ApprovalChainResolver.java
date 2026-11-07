package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.ApproverRef;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScope;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Who approves a request, step by step (D4). The chain comes from the school policy's {@code approval_chains}
 * ({@code {"LEAVE":[{"minDays":0,"steps":["MANAGER"]},...]}}: the entry with the greatest {@code minDays} not above
 * the requested days), else the built-in defaults. {@code MANAGER} = the requester's {@code EmployeeProfile.managerId}
 * when that user is active; {@code SCHOOL_ROLE:X} = active holders of role {@code school.x} in the school (all of them
 * hold the step). A step with nobody falls back to the school's {@code timekeeping.approve} holders. The requester never
 * approves their own request: such a step is SKIPPED. When every step is skipped, one ESCALATION step goes to another
 * approve holder of the school, else to the tenant's {@code timekeeping.manage} holders; if still nobody, every step
 * stays skipped and the caller refuses the request.
 */
@Component
public class ApprovalChainResolver {

    public static final String MANAGER = "MANAGER";
    public static final String ESCALATION = "ESCALATION";
    static final String SCHOOL_ROLE = "SCHOOL_ROLE:";
    static final String VICE_PRINCIPAL = SCHOOL_ROLE + "VICE_PRINCIPAL";
    private static final String APPROVE = "timekeeping.approve";
    private static final String MANAGE = "timekeeping.manage";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ApprovalChainResolver.class);

    /** {@code approverIds} is empty exactly when {@code skipped}. */
    public record ChainStep(int stepNo, String resolver, List<String> approverIds, boolean skipped) {}

    private final EmployeeProfileRepository profiles;
    private final UserRepository users;
    private final UserAccessScopeRepository scopes;
    private final WorkContextResolver context;
    private final Clock clock;

    ApprovalChainResolver(EmployeeProfileRepository profiles, UserRepository users, UserAccessScopeRepository scopes,
            WorkContextResolver context, Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.scopes = scopes;
        this.context = context;
        this.clock = clock;
    }

    /** The approvers a requester would get (preview): every approver of every non-skipped step, with names. */
    public List<ApproverRef> resolveChain(String tenantId, String requesterId, RequestType type, BigDecimal days) {
        return context.schoolOf(tenantId, requesterId)
                .map(school -> refs(tenantId, resolve(tenantId, school, requesterId, type, days)))
                .orElse(List.of());
    }

    public List<ApproverRef> refs(String tenantId, List<ChainStep> steps) {
        Set<String> ids = steps.stream().flatMap(s -> s.approverIds().stream()).collect(Collectors.toSet());
        Map<String, String> names = names(tenantId, ids);
        List<ApproverRef> refs = new ArrayList<>();
        for (ChainStep step : steps) {
            for (String id : step.approverIds()) {
                refs.add(new ApproverRef(step.stepNo(), step.resolver(), id, names.get(id), title(step.resolver())));
            }
        }
        return refs;
    }

    public Map<String, String> names(String tenantId, Collection<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return users.findByTenantIdAndIdInAnyStatus(tenantId, userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() == null ? "" : u.getFullName(), (a, b) -> a));
    }

    public List<ChainStep> resolve(String tenantId, String schoolId, String requesterId, RequestType type, BigDecimal days) {
        LocalDate today = LocalDate.now(clock.withZone(WorkContextResolver.ZONE));
        Holders holders = new Holders(tenantId, schoolId, today);
        List<ChainStep> steps = new ArrayList<>();
        int stepNo = 1;
        for (String resolver : resolvers(context.policyFor(tenantId, schoolId, today).getApprovalChains(), type, days)) {
            List<String> candidates = candidates(tenantId, schoolId, requesterId, resolver);
            List<String> approvers = without(candidates.isEmpty() ? holders.approve() : candidates, requesterId);
            steps.add(new ChainStep(stepNo++, resolver, approvers, approvers.isEmpty()));
        }
        if (steps.stream().allMatch(ChainStep::skipped)) {
            List<String> escalated = without(holders.approve(), requesterId);
            if (escalated.isEmpty()) {
                escalated = without(users.findActiveUserIdsHoldingPermission(tenantId, MANAGE, today), requesterId);
            }
            if (!escalated.isEmpty()) steps.add(new ChainStep(stepNo, ESCALATION, escalated, false));
        }
        return steps;
    }

    static String title(String resolver) {
        if (MANAGER.equals(resolver)) return "Quản lý trực tiếp";
        if (VICE_PRINCIPAL.equals(resolver)) return "Phó hiệu trưởng";
        return "Người duyệt";
    }

    private List<String> candidates(String tenantId, String schoolId, String requesterId, String resolver) {
        if (MANAGER.equals(resolver)) {
            String managerId = profiles.findByUserIdAndTenantId(requesterId, tenantId)
                    .map(EmployeeProfile::getManagerId).filter(id -> !id.isBlank()).orElse(null);
            if (managerId == null) return List.of();
            if (managerId.equals(requesterId)) return List.of(managerId);
            return users.findByTenantIdAndIdIn(tenantId, List.of(managerId)).isEmpty() ? List.of() : List.of(managerId);
        }
        if (resolver.startsWith(SCHOOL_ROLE)) {
            String role = "school." + resolver.substring(SCHOOL_ROLE.length()).toLowerCase();
            return inSchool(users.findActiveUserIdsHoldingRole(tenantId, role), schoolId);
        }
        log.warn("Unknown approval resolver in tenant {}: {}", tenantId, resolver);
        return List.of();
    }

    private List<String> inSchool(List<String> userIds, String schoolId) {
        if (userIds.isEmpty()) return List.of();
        Set<String> members = scopes.findByUserIdInOrderByCreatedAtAsc(userIds).stream()
                .filter(scope -> schoolId.equals(scope.getEducationUnitId()))
                .map(UserAccessScope::getUserId).collect(Collectors.toSet());
        return userIds.stream().filter(members::contains).distinct().toList();
    }

    private static List<String> without(List<String> ids, String requesterId) {
        return ids.stream().filter(id -> !id.equals(requesterId)).toList();
    }

    /** Configured chain for the type, else the defaults (LEAVE under 1 day: manager only). Bad JSON: defaults. */
    static List<String> resolvers(String json, RequestType type, BigDecimal days) {
        try {
            Object entries = JSON.readValue(json == null || json.isBlank() ? "{}" : json, Map.class).get(type.name());
            List<String> best = null;
            BigDecimal bestMin = null;
            if (entries instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> rule) || !(rule.get("steps") instanceof List<?> steps)) continue;
                    BigDecimal min = rule.get("minDays") instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
                    if (min.compareTo(days) <= 0 && (bestMin == null || min.compareTo(bestMin) > 0) && !steps.isEmpty()) {
                        bestMin = min;
                        best = steps.stream().map(String::valueOf).toList();
                    }
                }
            }
            if (best != null) return best;
        } catch (RuntimeException invalid) {
            log.warn("Invalid approval_chains for {}: {}", type, invalid.getClass().getSimpleName());
        }
        return switch (type) {
            case LEAVE -> days.compareTo(BigDecimal.ONE) < 0 ? List.of(MANAGER) : List.of(MANAGER, VICE_PRINCIPAL);
            case BUSINESS -> List.of(MANAGER, VICE_PRINCIPAL);
            case EXPLANATION, WFH -> List.of(MANAGER);
        };
    }

    /** The school's approve holders, read once per resolution. */
    private final class Holders {
        private final String tenantId;
        private final String schoolId;
        private final LocalDate today;
        private List<String> approve;

        Holders(String tenantId, String schoolId, LocalDate today) {
            this.tenantId = tenantId;
            this.schoolId = schoolId;
            this.today = today;
        }

        List<String> approve() {
            if (approve == null) {
                approve = inSchool(users.findActiveUserIdsHoldingPermission(tenantId, APPROVE, today), schoolId);
            }
            return approve;
        }
    }

}
