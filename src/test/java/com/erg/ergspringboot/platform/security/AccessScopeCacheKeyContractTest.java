package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase;
import com.erg.ergspringboot.organization.internal.OrganizationEducationUnitQuery;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

class AccessScopeCacheKeyContractTest {

    @Test
    void authorizationSensitiveCachesUseTheCompleteStableScopeFingerprint() throws Exception {
        Cacheable educationUnits = OrganizationEducationUnitQuery.class
                .getMethod("list", UserAccessContext.class)
                .getAnnotation(Cacheable.class);
        Cacheable classes = LmsClassroomQueryUseCase.class
                .getMethod("listClassesBySchool", String.class, UserAccessContext.class)
                .getAnnotation(Cacheable.class);

        assertScopeFingerprint(educationUnits);
        assertScopeFingerprint(classes);
    }

    private void assertScopeFingerprint(Cacheable cacheable) {
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.key())
                .contains("#access.tenantId()")
                .contains("#access.scopeFingerprint()")
                .doesNotContain("managedEducationUnitIds")
                .doesNotContain("managedClassIds");
    }
}
