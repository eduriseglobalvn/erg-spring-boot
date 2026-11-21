package com.erg.ergspringboot.timekeeping.internal;

import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/**
 * The schools of the tenant a timekeeping admin caller may read or write. {@code schools == null} is the whole tenant
 * (platform-wide principal or a managed education-unit scope holding "*"); anything else is the caller's managed
 * education units. Same rule as {@code CalendarScopeService.requireManagedSchools}, copied so the module owns it.
 */
public record SchoolScope(Set<String> schools) {

    public static final SchoolScope TENANT = new SchoolScope(null);

    /** 403 SCHOOL_SCOPE_FORBIDDEN when the caller manages no school at all. */
    public static SchoolScope of(ErgPrincipal principal) {
        List<String> managed = AccessScopeAssembler.from(principal).managedIds(AccessScopeSet.EDUCATION_UNIT);
        if (managed.contains("*")) return TENANT;
        if (managed.isEmpty()) throw forbidden();
        return new SchoolScope(Set.copyOf(managed));
    }

    public boolean wide() {
        return schools == null;
    }

    public boolean allows(String schoolId) {
        return schools == null || schools.contains(schoolId);
    }

    /** 403 unless the caller runs the whole tenant: closing or reopening a pay period is Phòng đào tạo's call (owner decision 21/11/2026). */
    public SchoolScope requireWide() {
        if (!wide()) throw forbidden();
        return this;
    }

    /** 403 unless {@code schoolId} is inside the scope. */
    public void require(String schoolId) {
        if (!allows(schoolId)) throw forbidden();
    }

    /** A school-bound write on a row that may have no school (null = tenant-level row): only a wide caller may touch it. */
    public void requireRow(String schoolId) {
        if (schoolId == null ? !wide() : !allows(schoolId)) throw forbidden();
    }

    /** {@code wanted} narrowed to the scope: all of it for a wide caller. */
    public Set<String> restrict(Collection<String> wanted) {
        return wide() ? Set.copyOf(wanted) : wanted.stream().filter(schools::contains).collect(Collectors.toSet());
    }

    /** The schools a read covers: null = whole tenant (wide caller, no school asked); a named school must be in scope. */
    public List<String> schoolsFor(String schoolId) {
        if (schoolId != null && !schoolId.isBlank()) {
            require(schoolId);
            return List.of(schoolId);
        }
        return schools == null ? null : List.copyOf(schools);
    }

    private static TimekeepingException forbidden() {
        return new TimekeepingException(HttpStatus.FORBIDDEN, "SCHOOL_SCOPE_FORBIDDEN",
                "Bạn không có quyền với cơ sở này");
    }
}
