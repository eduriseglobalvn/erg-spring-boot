package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses;
import com.erg.ergspringboot.platform.cache.CacheInvalidationMessage;
import com.erg.ergspringboot.platform.cache.CacheRuntimeHintsContributor;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.stereotype.Component;

/** Native hints for platform-owned cache graphs and cache security expressions. */
@Component
public class PlatformCacheRuntimeHintsContributor implements CacheRuntimeHintsContributor {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Set<Class<?>> visited = ConcurrentHashMap.newKeySet();
        for (Class<?> type : new Class<?>[]{
                CalendarResponses.class, EducationUnitResponses.class, SchoolOperationsResponses.class,
                CurriculumDtos.class, LearningResourceLibraryResponses.class,
                ErgPrincipal.class, UserAccessContext.class, CacheInvalidationMessage.class}) {
            register(hints, type, visited);
        }
    }

    private void register(RuntimeHints hints, Class<?> type, Set<Class<?>> visited) {
        if (!visited.add(type)) return;
        hints.reflection().registerType(type,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        for (Class<?> nested : type.getDeclaredClasses()) register(hints, nested, visited);
    }
}
