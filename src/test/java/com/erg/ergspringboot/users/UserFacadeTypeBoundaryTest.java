package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UserFacadeTypeBoundaryTest {
    @Test
    void publicUserFacadesDoNotExposeLegacyServiceTypes() {
        for (Class<?> facade : new Class<?>[]{UserOperations.class, EmployeeOperations.class}) {
            for (Method method : facade.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                assertThat(method.getReturnType().getPackageName()).doesNotContain(".service.");
                assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getPackageName))
                        .noneMatch(packageName -> packageName.contains(".service."));
            }
        }
    }
}
