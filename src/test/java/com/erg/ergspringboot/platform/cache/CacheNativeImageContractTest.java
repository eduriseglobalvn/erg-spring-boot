package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class CacheNativeImageContractTest {

    @Test
    void registersReflectionHintsForL2DtoGraphs() {
        RuntimeHints hints = new RuntimeHints();
        new BusinessCacheRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(CalendarWorkspaceResponse.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(StudentDashboardResponse.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(StudentDashboardReadScope.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(ErgPrincipal.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(UserAccessContext.class).test(hints)).isTrue();
    }
}
