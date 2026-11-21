package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable copy of what one school has configured for timekeeping (every shift, the active sites, every policy
 * version): the cached value, never the JPA entities. {@link #toShift}, {@link #toSite} and {@link #toPolicy} hand
 * callers fresh detached entities, so a caller that edits one cannot reach the cache.
 */
public record SchoolSetup(List<ShiftCopy> shifts, List<SiteCopy> sites, List<PolicyCopy> policies) {

    public record ShiftCopy(String id, OffsetDateTime createdAt, OffsetDateTime updatedAt, String tenantId, String schoolId,
            String name, LocalTime startTime, LocalTime endTime, BigDecimal workUnits, List<Integer> weekdays,
            boolean active, Instant deactivatedAt) {}

    public record SiteCopy(String id, OffsetDateTime createdAt, OffsetDateTime updatedAt, String tenantId, String schoolId,
            String name, String address, BigDecimal lat, BigDecimal lng, int radiusM, boolean active) {}

    public record PolicyCopy(String id, OffsetDateTime createdAt, OffsetDateTime updatedAt, String tenantId, String schoolId,
            LocalDate effectiveFrom, int lateToleranceMin, int lateBlockMin, boolean requireFace, boolean allowOutside,
            int explanationsPerMonth, int explanationWindowDays, int annualLeaveDays, int seniorityStepYears,
            int lockDayOfMonth, String approvalChains) {}

    static SchoolSetup of(List<TkShift> shifts, List<TkSite> sites, List<TkPolicy> policies) {
        return new SchoolSetup(
                shifts.stream().map(s -> new ShiftCopy(s.getId(), s.getCreatedAt(), s.getUpdatedAt(), s.getTenantId(),
                        s.getSchoolId(), s.getName(), s.getStartTime(), s.getEndTime(), s.getWorkUnits(),
                        List.of(s.getWeekdays()), Boolean.TRUE.equals(s.getActive()), s.getDeactivatedAt())).toList(),
                sites.stream().map(s -> new SiteCopy(s.getId(), s.getCreatedAt(), s.getUpdatedAt(), s.getTenantId(),
                        s.getSchoolId(), s.getName(), s.getAddress(), s.getLat(), s.getLng(), s.getRadiusM(),
                        Boolean.TRUE.equals(s.getActive()))).toList(),
                policies.stream().map(p -> new PolicyCopy(p.getId(), p.getCreatedAt(), p.getUpdatedAt(), p.getTenantId(),
                        p.getSchoolId(), p.getEffectiveFrom(), p.getLateToleranceMin(), p.getLateBlockMin(),
                        p.getRequireFace(), p.getAllowOutside(), p.getExplanationsPerMonth(), p.getExplanationWindowDays(),
                        p.getAnnualLeaveDays(), p.getSeniorityStepYears(), p.getLockDayOfMonth(),
                        p.getApprovalChains())).toList());
    }

    /** True when the school has a shift or an active site: the test for "timekeeping is configured here". */
    public boolean configured() {
        return !shifts.isEmpty() || !sites.isEmpty();
    }

    static TkShift toShift(ShiftCopy c) {
        TkShift s = new TkShift();
        s.setId(c.id());
        s.setCreatedAt(c.createdAt());
        s.setUpdatedAt(c.updatedAt());
        s.setTenantId(c.tenantId());
        s.setSchoolId(c.schoolId());
        s.setName(c.name());
        s.setStartTime(c.startTime());
        s.setEndTime(c.endTime());
        s.setWorkUnits(c.workUnits());
        s.setWeekdays(c.weekdays().toArray(Integer[]::new));
        s.setActive(c.active());
        s.setDeactivatedAt(c.deactivatedAt());
        return s;
    }

    static TkSite toSite(SiteCopy c) {
        TkSite s = new TkSite();
        s.setId(c.id());
        s.setCreatedAt(c.createdAt());
        s.setUpdatedAt(c.updatedAt());
        s.setTenantId(c.tenantId());
        s.setSchoolId(c.schoolId());
        s.setName(c.name());
        s.setAddress(c.address());
        s.setLat(c.lat());
        s.setLng(c.lng());
        s.setRadiusM(c.radiusM());
        s.setActive(c.active());
        return s;
    }

    static TkPolicy toPolicy(PolicyCopy c) {
        TkPolicy p = new TkPolicy();
        p.setId(c.id());
        p.setCreatedAt(c.createdAt());
        p.setUpdatedAt(c.updatedAt());
        p.setTenantId(c.tenantId());
        p.setSchoolId(c.schoolId());
        p.setEffectiveFrom(c.effectiveFrom());
        p.setLateToleranceMin(c.lateToleranceMin());
        p.setLateBlockMin(c.lateBlockMin());
        p.setRequireFace(c.requireFace());
        p.setAllowOutside(c.allowOutside());
        p.setExplanationsPerMonth(c.explanationsPerMonth());
        p.setExplanationWindowDays(c.explanationWindowDays());
        p.setAnnualLeaveDays(c.annualLeaveDays());
        p.setSeniorityStepYears(c.seniorityStepYears());
        p.setLockDayOfMonth(c.lockDayOfMonth());
        p.setApprovalChains(c.approvalChains());
        return p;
    }
}
