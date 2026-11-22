package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.notifications.RecipientLookupResponses.RecipientCard;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The people-picker directory as lightweight rows (no entities), cached per tenant and scope for 45 s. The scope
 * key (whole tenant, or the caller's class set) is part of the cache key, so a teacher never reads an admin's
 * entries. Student, class and account writes are not hooked: a change shows up after the TTL at most. Login status
 * is deliberately not cached here; the lookup re-checks it live for the few people it returns.
 */
@Component
public class RecipientDirectoryCache {

    // shortcut: names are matched in memory (no unaccent in the DB); lists past this many students are cut, move to an unaccent column then.
    static final int DIRECTORY_CAP = 20_000;
    private static final int STAFF_CAP = NotificationAudienceResolver.MAX_RECIPIENTS + 1;
    private static final Pageable STAFF_PAGE = PageRequest.ofSize(STAFF_CAP);
    private static final String SEP = " · ";
    private static final Logger log = LoggerFactory.getLogger(RecipientDirectoryCache.class);

    /** One addressable person; {@code folded} is the accent-free lower-case name used for matching. */
    public record Entry(String userId, String name, String role, String meta,
                        String code, String email, String phone, boolean student, String folded) {
        RecipientCard card() {
            return new RecipientCard(userId, name, role, meta);
        }

        static Entry ofStudent(AudienceAccountQueries.StudentRow s) {
            return new Entry(s.userId(), s.fullName(), "STUDENT", join("Học sinh", s.className(), s.studentCode()),
                    s.studentCode(), s.email(), s.phone(), true, RecipientLookupService.fold(s.fullName()));
        }

        static Entry ofUser(UserAccountProjection u, String role, String label) {
            return new Entry(u.id(), u.displayName(), role, join(label, u.email()), null, u.email(), null, false,
                    RecipientLookupService.fold(u.displayName()));
        }
    }

    public record Directory(List<Entry> entries) {}

    private final AudienceAccountQueries accounts;
    private final UserDirectoryPort users;

    public RecipientDirectoryCache(AudienceAccountQueries accounts, UserDirectoryPort users) {
        this.accounts = accounts;
        this.users = users;
    }

    /** Students on the roll with a login; {@code classIds == null} is the whole tenant. {@code scopeKey} identifies it. */
    @Cacheable(cacheNames = "notificationRecipientDirectory", sync = true, key = "{#tenantId, #scopeKey}")
    public Directory students(String tenantId, String scopeKey, Collection<String> classIds) {
        List<AudienceAccountQueries.StudentRow> rows = accounts.studentDirectory(tenantId, classIds, DIRECTORY_CAP);
        if (rows.size() >= DIRECTORY_CAP) {
            log.warn("Student directory hit its cap of {} for tenant {}; name search is truncated", DIRECTORY_CAP, tenantId);
        }
        return new Directory(rows.stream().map(Entry::ofStudent).toList());
    }

    /** Active teachers and staff of the tenant (admins only see them). */
    @Cacheable(cacheNames = "notificationRecipientDirectory", sync = true, key = "{#tenantId, 'staff'}")
    public Directory staff(String tenantId) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        users.findCalendarTeachersByTenant(tenantId, STAFF_PAGE).stream().filter(RecipientDirectoryCache::isActive)
                .forEach(u -> byId.putIfAbsent(u.id(), Entry.ofUser(u, "TEACHER", "Giáo viên")));
        if (byId.size() >= STAFF_CAP) {
            log.warn("Teacher directory hit its cap of {} for tenant {}", STAFF_CAP, tenantId);
        }
        List<String> staffIds = users.findActiveStaffUserIds(tenantId, STAFF_PAGE);
        if (staffIds.size() >= STAFF_CAP) {
            log.warn("Staff directory hit its cap of {} for tenant {}", STAFF_CAP, tenantId);
        }
        if (!staffIds.isEmpty()) {
            users.findByTenantIdAndIds(tenantId, staffIds).stream().filter(RecipientDirectoryCache::isActive)
                    .forEach(u -> byId.putIfAbsent(u.id(), Entry.ofUser(u, "STAFF", "Nhân viên")));
        }
        return new Directory(List.copyOf(byId.values()));
    }

    static boolean isActive(UserAccountProjection user) {
        return user.status() == null || "ACTIVE".equalsIgnoreCase(user.status());
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(SEP));
    }
}
