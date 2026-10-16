package com.erg.ergspringboot.users;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;

/**
 * Small read port for cross-module account directory queries.
 *
 * <p>The port is intentionally expressed only in scalar values and immutable
 * projections.  Implementations may use the current JPA model, but callers
 * must not depend on that persistence detail.</p>
 */
public interface UserDirectoryPort {

    List<UserAccountProjection> findCalendarTeachersByTenant(String tenantId, Pageable pageable);

    List<UserAccountProjection> findCalendarTeachersByTenant(String tenantId);

    List<UserAccountProjection> findCalendarTeachersByTenantAndSchoolIds(
            String tenantId, Collection<String> schoolIds, Pageable pageable);

    List<UserAccountProjection> findCalendarTeachersByTenantAndSchoolIds(
            String tenantId, Collection<String> schoolIds);

    List<UserAccountProjection> findByTenantIdAndIds(String tenantId, Collection<String> ids);
}
