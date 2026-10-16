package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantScopedLmsStoreContractTest {

    @Test
    void tenantOwnedStoresDoNotExposeUnscopedFindById() {
        for (Class<?> store : tenantOwnedStores()) {
            assertThat(Arrays.stream(store.getDeclaredMethods())
                            .filter(method -> method.getName().equals("findById")))
                    .as(store.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void tenantOwnedStoresRequireTenantAndIdForSingleAggregateReads() {
        for (Class<?> store : tenantOwnedStores()) {
            assertThat(Arrays.stream(store.getDeclaredMethods())
                            .filter(method -> method.getName().equals("findByTenantIdAndId"))
                            .map(Method::getParameterTypes))
                    .as(store.getSimpleName())
                    .singleElement()
                    .satisfies(parameters -> assertThat(parameters)
                            .containsExactly(String.class, String.class));
        }
    }

    private List<Class<?>> tenantOwnedStores() {
        return List.of(
                LmsAssignmentService.class,
                LmsAttemptService.class,
                LmsClassroomService.class,
                LmsQuizService.class
        );
    }
}
