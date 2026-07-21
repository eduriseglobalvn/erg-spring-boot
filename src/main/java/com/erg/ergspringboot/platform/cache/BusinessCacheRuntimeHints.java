package com.erg.ergspringboot.platform.cache;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses;
import com.erg.ergspringboot.common.response.organization.EducationUnitResponses;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses;
import com.erg.ergspringboot.common.response.school.SchoolOperationsResponses;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public final class BusinessCacheRuntimeHints implements RuntimeHintsRegistrar {
    private static final Class<?>[] DTO_CONTAINERS = {
            CalendarResponses.class, StudentDashboardResponses.class, LmsRuntimeDtos.class,
            LcmsWorkspaceResponses.class, LmsClassWorkspaceResponses.class,
            LmsHomeworkWorkspaceResponses.class, EducationUnitResponses.class,
            QuizReportResponses.class, QuizWorkspaceResponses.class, SchoolOperationsResponses.class,
            CurriculumDtos.class, LearningResourceLibraryResponses.class
    };
    private static final Class<?>[] SPEL_TYPES = {
            StudentDashboardReadScope.class, ErgPrincipal.class, UserAccessContext.class
    };
    private static final Class<?>[] EVENT_TYPES = {
            CacheInvalidationMessage.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Set<Class<?>> visited = ConcurrentHashMap.newKeySet();
        for (Class<?> container : DTO_CONTAINERS) {
            for (Class<?> nested : container.getDeclaredClasses()) register(hints, nested, visited);
        }
        for (Class<?> type : SPEL_TYPES) register(hints, type, visited);
        for (Class<?> type : EVENT_TYPES) register(hints, type, visited);
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
