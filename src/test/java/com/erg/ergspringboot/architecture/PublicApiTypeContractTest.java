package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class PublicApiTypeContractTest {
    private static final Set<String> LEGACY_DYNAMIC_MAP_ALLOWLIST = Set.of(
            "AuthController#logout",
            "AuthController#logoutAll",
            "LcmsQuizBundleController#exportActive",
            "LcmsQuizBundleController#exportVersion",
            "LearningContentController#deleteContentItem",
            "NotificationDeviceController#revoke",
            "NotificationDeviceController#unregister"
    );

    @Test
    void publicControllerMapsAreRestrictedToReviewedLegacyAllowlist() throws ClassNotFoundException {
        Set<String> dynamicEndpoints = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (var bean : scanner.findCandidateComponents("com.erg.ergspringboot.controller")) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            for (var method : controller.getMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                boolean hasDynamicMap = containsMap(method.getGenericReturnType());
                for (Type parameterType : method.getGenericParameterTypes()) {
                    hasDynamicMap |= containsMap(parameterType);
                }
                if (hasDynamicMap) {
                    dynamicEndpoints.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(dynamicEndpoints)
                .as("Every dynamic Map endpoint must be reviewed; migrate it to a typed DTO, or add a temporary named allowlist entry")
                .containsExactlyInAnyOrderElementsOf(LEGACY_DYNAMIC_MAP_ALLOWLIST);
    }

    private boolean containsMap(Type type) {
        if (type instanceof Class<?> rawType) {
            return Map.class.isAssignableFrom(rawType);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (containsMap(parameterizedType.getRawType())) {
                return true;
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsMap(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsMap(arrayType.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                if (containsMap(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                if (containsMap(bound)) {
                    return true;
                }
            }
        }
        return false;
    }
}
