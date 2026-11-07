package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLockRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicyRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRule;
import com.erg.ergspringboot.timekeeping.persistence.TkRemoteRuleRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScope;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The one place that answers "which school, shift, policy, sites and rules apply to this person on this day"
 * (shared by punches, my views, day aggregation and the admin API). There is no explicit person-to-shift model:
 * a school has at most one shift per weekday, and the person's school is their first access scope that has
 * timekeeping configured.
 */
@Component
public class WorkContextResolver {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserAccessScopeRepository scopes;
    private final TkShiftRepository shifts;
    private final TkSiteRepository sites;
    private final TkPolicyRepository policies;
    private final TkRemoteRuleRepository remoteRules;
    private final TkPeriodLockRepository locks;
    private final TkRequestRepository requests;

    WorkContextResolver(UserAccessScopeRepository scopes, TkShiftRepository shifts, TkSiteRepository sites,
            TkPolicyRepository policies, TkRemoteRuleRepository remoteRules, TkPeriodLockRepository locks,
            TkRequestRepository requests) {
        this.scopes = scopes;
        this.shifts = shifts;
        this.sites = sites;
        this.policies = policies;
        this.remoteRules = remoteRules;
        this.locks = locks;
        this.requests = requests;
    }

    public Optional<String> schoolOf(String tenantId, String userId) {
        List<String> units = scopes.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(UserAccessScope::getEducationUnitId).distinct().toList();
        return units.stream()
                .filter(unit -> shifts.existsByTenantIdAndSchoolId(tenantId, unit)
                        || sites.existsByTenantIdAndSchoolIdAndActiveTrue(tenantId, unit))
                .findFirst();
    }

    /** {@link #schoolOf} for many people in three queries (daily close); people without a school are left out. */
    public Map<String, String> schoolsOf(String tenantId, Collection<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Set<String> configured = new HashSet<>();
        shifts.findByTenantId(tenantId).forEach(shift -> configured.add(shift.getSchoolId()));
        sites.findByTenantIdAndActiveTrue(tenantId).forEach(site -> configured.add(site.getSchoolId()));
        Map<String, String> schools = new HashMap<>();
        for (UserAccessScope scope : scopes.findByUserIdInOrderByCreatedAtAsc(userIds)) {
            if (configured.contains(scope.getEducationUnitId())) {
                schools.putIfAbsent(scope.getUserId(), scope.getEducationUnitId());
            }
        }
        return schools;
    }

    public Optional<TkShift> shiftFor(String tenantId, String schoolId, LocalDate date) {
        return shiftOn(shiftsOf(tenantId, schoolId), date);
    }

    public List<TkShift> shiftsOf(String tenantId, String schoolId) {
        return shifts.findByTenantIdAndSchoolIdOrderByStartTimeAsc(tenantId, schoolId);
    }

    /** The school's shift for the date's ISO weekday, from a list already loaded with {@link #shiftsOf}. */
    public static Optional<TkShift> shiftOn(List<TkShift> schoolShifts, LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        return schoolShifts.stream().filter(shift -> Arrays.asList(shift.getWeekdays()).contains(weekday)).findFirst();
    }

    /** Falls back to the column defaults (tolerance 5, block 15, face required, ...) when nothing is configured. */
    public TkPolicy policyFor(String tenantId, String schoolId, LocalDate date) {
        return policies.findFirstByTenantIdAndSchoolIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                tenantId, schoolId, date).orElseGet(() -> {
                    TkPolicy defaults = new TkPolicy();
                    defaults.setTenantId(tenantId);
                    defaults.setSchoolId(schoolId);
                    defaults.setEffectiveFrom(date);
                    return defaults;
                });
    }

    public List<TkSite> activeSites(String tenantId, String schoolId) {
        return sites.findByTenantIdAndSchoolIdAndActiveTrueOrderByNameAsc(tenantId, schoolId);
    }

    /** A rule applies when it names the person, names the weekday, or the person has an approved WFH that day. */
    public boolean isRemote(String tenantId, String schoolId, String userId, LocalDate date, String shiftId) {
        int weekday = date.getDayOfWeek().getValue();
        for (TkRemoteRule rule : remoteRules.findByTenantIdAndSchoolIdAndActiveTrue(tenantId, schoolId)) {
            if (rule.getShiftId() != null && !rule.getShiftId().equals(shiftId)) continue;
            boolean applies = switch (rule.getKind()) {
                case USERS -> rule.getUserIds() != null && rule.getUserIds().contains(userId);
                case WEEKDAY -> rule.getWeekday() != null && rule.getWeekday() == weekday;
                case APPROVED_WFH -> requests.hasApprovedWfh(tenantId, userId, date);
            };
            if (applies) return true;
        }
        return false;
    }

    public boolean isLocked(String tenantId, String schoolId, LocalDate date) {
        return locks.existsByTenantIdAndSchoolIdAndPeriodAndReopenedAtIsNull(tenantId, schoolId, date.format(PERIOD));
    }

    /** Guard for every write into a period: punches, requests, admin edits. */
    public void requireOpen(String tenantId, String schoolId, LocalDate date) {
        if (isLocked(tenantId, schoolId, date)) throw periodLocked();
    }

    public static TimekeepingException periodLocked() {
        return new TimekeepingException(HttpStatus.CONFLICT, "PERIOD_LOCKED", "Kỳ công đã chốt");
    }
}
