package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordUseCase;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import tools.jackson.databind.json.JsonMapper;

/** The class-scoped teacher caches: key position 1 is the class (the scope the invalidator drops), TTL 30 s. */
class LmsClassReadCachesContractTest {

    @Test
    void bothCachesAreDescribedWithTheClassAsScopeAndA30SecondTtl() {
        var descriptors = new LmsReadCacheDescriptorContributor().descriptors(JsonMapper.builder().findAndAddModules().build());

        assertThat(descriptors).extracting(d -> d.name()).containsExactlyInAnyOrder("lmsClassScoreBoard", "lmsAttendanceWorkspace");
        assertThat(descriptors).allSatisfy(d -> assertThat(d.l1Ttl()).hasSeconds(30));
        assertThat(LmsReadCacheDescriptorContributor.classScope().apply(List.of("tenant", "class-1", "x"))).isEqualTo("class-1");
        assertThat(LmsReadCacheDescriptorContributor.classScope().apply("not-a-list")).isNull();
    }

    @Test
    void theCacheableKeysPutTenantThenClassFirst() {
        assertThat(keyOf(LmsClassScoreBoardOperations.class, "scoreBoard", "lmsClassScoreBoard")).startsWith("#tenantId, #classId");
        assertThat(keyOf(LmsAcademicRecordUseCase.class, "attendanceWorkspace", "lmsAttendanceWorkspace"))
                .startsWith("#tenantId, #classId").contains("#subjectId", "#fromValue", "#toValue");
    }

    private static String keyOf(Class<?> type, String method, String cacheName) {
        Method found = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
        Cacheable cacheable = found.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(cacheName);
        return cacheable.key().replaceAll("^\\{|}$", "");
    }
}
