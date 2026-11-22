package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicyRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per (tenant, school) cache of the shifts, active sites and policy versions every punch, view and day rebuild asks
 * for. A separate bean so the {@code @Cacheable} proxy applies. Short TTL (see the descriptor) plus {@link #evict},
 * called by every writer of those three tables, so an admin edit shows at once on this instance and within the TTL on
 * the others. Punches, requests, locks and authorization are never cached here.
 */
@Component
public class SchoolSetupCache {

    public static final String CACHE = "timekeepingSchoolSetup";

    private final TkShiftRepository shifts;
    private final TkSiteRepository sites;
    private final TkPolicyRepository policies;
    private final LmsReadCacheInvalidator invalidator;

    SchoolSetupCache(TkShiftRepository shifts, TkSiteRepository sites, TkPolicyRepository policies,
            LmsReadCacheInvalidator invalidator) {
        this.shifts = shifts;
        this.sites = sites;
        this.policies = policies;
        this.invalidator = invalidator;
    }

    /** Key is the list {tenant, school}: the school (element 1) is the invalidation scope. */
    @Cacheable(cacheNames = CACHE, key = "{#tenantId, #schoolId}")
    @Transactional(readOnly = true)
    public SchoolSetup get(String tenantId, String schoolId) {
        return SchoolSetup.of(shifts.findByTenantIdAndSchoolIdOrderByStartTimeAsc(tenantId, schoolId),
                sites.findByTenantIdAndSchoolIdAndActiveTrueOrderByNameAsc(tenantId, schoolId),
                policies.findByTenantIdAndSchoolId(tenantId, schoolId));
    }

    /** Drops the school's entry now and again after commit (a null school is ignored). */
    public void evict(String tenantId, String schoolId) {
        if (schoolId != null && !schoolId.isBlank()) {
            invalidator.evictScopesAfterCommit(tenantId, Map.of(CACHE, List.of(schoolId)));
        }
    }
}
