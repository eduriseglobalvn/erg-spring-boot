package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * A module listener runs on an async thread with no TenantContext. With the default REQUIRES_NEW its transaction
 * starts before the method body, so app.tenant_id is cleared and RLS hides every tenant row (production 25/11/2026:
 * no teacher got a schedule notification). Each listener must declare NOT_SUPPORTED and open
 * TenantContext.scope(event.tenantId()) itself, so the services it calls begin their transactions inside the tenant.
 */
class ModuleListenerTenantScopeTest {

    @Test
    void everyModuleListenerStartsOutsideATransaction() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> listeners = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.erg.ergspringboot")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                ApplicationModuleListener listener = method.getAnnotation(ApplicationModuleListener.class);
                if (listener == null) continue;
                String name = method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                        + "(" + method.getParameterTypes()[0].getSimpleName() + ")";
                listeners.add(name);
                if (listener.propagation() != Propagation.NOT_SUPPORTED) wrong.add(name);
            }
        }
        assertThat(listeners).isNotEmpty();
        assertThat(wrong).as("module listeners still opening a transaction before the tenant is set").isEmpty();
    }
}
