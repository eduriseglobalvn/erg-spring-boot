package com.erg.ergspringboot.users.internal.directory;

import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Compatibility implementation of the user directory port over the legacy
 * JPA repository.  This is the only calendar-directory adapter that may know
 * about {@code model.users.User} or {@code UserRepository}.
 */
@Service
@Deprecated(since = "2026-08-08", forRemoval = false)
public final class LegacyUserDirectoryAdapter implements UserDirectoryPort {

    private final UserRepository users;

    public LegacyUserDirectoryAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    public List<UserAccountProjection> findCalendarTeachersByTenant(String tenantId, Pageable pageable) {
        return map(users.findCalendarTeachersByTenant(tenantId, pageable), tenantId);
    }

    @Override
    public List<UserAccountProjection> findCalendarTeachersByTenant(String tenantId) {
        return map(users.findCalendarTeachersByTenant(tenantId), tenantId);
    }

    @Override
    public List<UserAccountProjection> findCalendarTeachersByTenantAndSchoolIds(
            String tenantId, Collection<String> schoolIds, Pageable pageable) {
        return map(users.findCalendarTeachersByTenantAndSchoolIds(tenantId, schoolIds, pageable), tenantId);
    }

    @Override
    public List<UserAccountProjection> findCalendarTeachersByTenantAndSchoolIds(
            String tenantId, Collection<String> schoolIds) {
        return map(users.findCalendarTeachersByTenantAndSchoolIds(tenantId, schoolIds), tenantId);
    }

    @Override
    public List<UserAccountProjection> findByTenantIdAndIds(String tenantId, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return map(users.findByTenantIdAndIdInAnyStatus(tenantId, ids), tenantId);
    }

    private List<UserAccountProjection> map(List<User> values, String tenantId) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null)
                .map(value -> map(value, tenantId))
                .toList();
    }

    private UserAccountProjection map(User value, String tenantId) {
        return new UserAccountProjection(
                value.getId(), tenantId, value.getFullName(), value.getEmail(), value.getStatus());
    }
}
