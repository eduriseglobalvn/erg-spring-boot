package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.config.CacheConfig;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BusinessCacheIsolationIntegrationTest {

    private static final List<String> APPROVED_L2_CACHES = List.of(
            "studentDashboard", "lmsClassWorkspace", "lmsHomeworkWorkspace",
            "lmsAssignmentProgress", "lmsStudentAssignments", "lmsStudentGroups", "lcmsQuestionBankWorkspace",
            "lcmsQuizWorkspace", "lcmsSchoolDirectory", "quizRuntimePackage",
            "quizQuestionAnalytics", "quizAssignmentReport", "organizationEducationUnits",
            "calendarWorkspace");

    @Test
    void everyApprovedBusinessCacheHasTenantSafeTypedDescriptor() {
        ErgTieredCacheManager manager = (ErgTieredCacheManager) new CacheConfig().cacheManager();

        assertThat(APPROVED_L2_CACHES).allSatisfy(name -> {
            CacheDescriptor<?> descriptor = manager.descriptor(name);
            assertThat(descriptor.tenantRequired()).as(name).isTrue();
            assertThat(descriptor.valueType().getRawClass()).as(name).isNotEqualTo(Object.class);
            assertThat(descriptor.maximumSerializedBytes()).as(name).isEqualTo(1024 * 1024);
            assertThat(descriptor.l2Ttl()).as(name).isGreaterThan(descriptor.l1Ttl());
        });
        assertThat(manager.descriptor("calendarWorkspace").valueType().getRawClass())
                .isEqualTo(CalendarWorkspaceResponse.class);
    }

    // Completeness guard: the approved list above only checks a subset. EVERY registered descriptor
    // must be tenant-scoped, so a future cache added with tenantRequired=false cannot silently ship a
    // cross-tenant leak. (Value type is intentionally not asserted here — several caches legitimately
    // hold generic/collection payloads; tenant scoping is the isolation invariant.)
    @Test
    void everyRegisteredCacheIsTenantScoped() {
        ErgTieredCacheManager manager = (ErgTieredCacheManager) new CacheConfig().cacheManager();

        assertThat(manager.getCacheNames()).isNotEmpty().allSatisfy(name -> {
            CacheDescriptor<?> descriptor = manager.descriptor(name);
            assertThat(descriptor.tenantRequired()).as("%s must be tenant-scoped", name).isTrue();
        });
    }

    // No business cache may be named after auth/session/secret material. Authorization role/permission
    // metadata caches are allowed (they are not tokens); raw credential/token/session data must not be
    // cached at all.
    @Test
    void noCacheNameLooksLikeSecretOrSessionMaterial() {
        ErgTieredCacheManager manager = (ErgTieredCacheManager) new CacheConfig().cacheManager();
        List<String> forbidden = List.of("token", "password", "secret", "credential", "session", "jwt", "refresh");

        assertThat(manager.getCacheNames()).allSatisfy(name -> {
            String lower = name.toLowerCase(Locale.ROOT);
            assertThat(forbidden).as("cache '%s' must not cache secret/session material", name)
                    .noneSatisfy(bad -> assertThat(lower).contains(bad));
        });
    }
}
