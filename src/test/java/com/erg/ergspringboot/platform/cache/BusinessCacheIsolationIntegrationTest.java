package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.config.CacheConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessCacheIsolationIntegrationTest {

    private static final List<String> APPROVED_L2_CACHES = List.of(
            "studentDashboard", "lmsClassWorkspace", "lmsHomeworkWorkspace",
            "lmsAssignmentProgress", "lmsStudentGroups", "lcmsQuestionBankWorkspace",
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
}
