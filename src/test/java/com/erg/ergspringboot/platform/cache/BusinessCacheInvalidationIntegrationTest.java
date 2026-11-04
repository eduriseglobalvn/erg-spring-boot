package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.internal.command.CalendarCommandUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarReadUseCase;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

class BusinessCacheInvalidationIntegrationTest {

    @Test
    void calendarReadsAreUserScopedAndEveryMutationInvalidatesTenantGeneration() {
        assertThat(Arrays.stream(CalendarReadUseCase.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("TeachingCalendarWorkspace")))
                .allSatisfy(method -> {
                    Cacheable cacheable = method.getAnnotation(Cacheable.class);
                    assertThat(cacheable).as(method.getName()).isNotNull();
                    assertThat(cacheable.cacheNames()).containsExactly("calendarWorkspace");
                    assertThat(cacheable.key()).as(method.getName()).contains("#tenantId").contains("#principal.userId()");
                });

        assertMutationInvalidates("create");
        assertMutationInvalidates("publishBatch");
        assertMutationInvalidates("cancelBatch");
        assertMutationInvalidates("updateEvent");
        assertMutationInvalidates("deleteEvent");
        assertMutationInvalidates("deleteTeacherSchedule");
        assertMutationInvalidates("createRoom");
    }

    @Test
    void externallyCalledPagedReportMethodOwnsItsCacheAnnotation() {
        Method method = Arrays.stream(QuizReportService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("assignmentReport")
                        && candidate.getParameterCount() == 4)
                .findFirst().orElseThrow();

        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.key()).contains("#tenantId", "#assignmentId", "#page", "#size");
    }

    private void assertMutationInvalidates(String methodName) {
        Method method = Arrays.stream(CalendarCommandUseCase.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        CacheEvict evict = method.getAnnotation(CacheEvict.class);
        assertThat(evict).as(methodName).isNotNull();
        assertThat(evict.cacheNames()).containsExactly("calendarWorkspace");
        assertThat(evict.allEntries()).isTrue();
        assertThat(evict.beforeInvocation()).isFalse();
    }
}
