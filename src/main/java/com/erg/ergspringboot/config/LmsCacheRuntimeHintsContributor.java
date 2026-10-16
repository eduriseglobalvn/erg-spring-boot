package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses;
import com.erg.ergspringboot.platform.cache.CacheRuntimeHintsContributor;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.stereotype.Component;

/** Native hints for LMS/LCMS/quiz/learner cache graphs. */
@Component
public class LmsCacheRuntimeHintsContributor implements CacheRuntimeHintsContributor {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Set<Class<?>> visited = ConcurrentHashMap.newKeySet();
        for (Class<?> container : new Class<?>[]{
                LmsRuntimeDtos.class, LcmsWorkspaceResponses.class, LmsClassWorkspaceResponses.class,
                LmsHomeworkWorkspaceResponses.class, StudentDashboardResponses.class,
                QuizReportResponses.class, QuizWorkspaceResponses.class, StudentDashboardReadScope.class}) {
            register(hints, container, visited);
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
