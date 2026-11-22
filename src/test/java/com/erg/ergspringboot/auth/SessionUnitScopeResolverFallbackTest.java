package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapScope;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SessionUnitScopeResolverFallbackTest {
    private final EducationUnitRepository units = mock(EducationUnitRepository.class);
    private final SessionUnitScopeResolver resolver = new SessionUnitScopeResolver(units, mock(ClassroomRepository.class),
            mock(UserRoleAssignmentRepository.class));
    private final ScopeCaller caller = new ScopeCaller(null, List.of(), List.of(), true, false, true, "erg", null);

    private static EducationUnit unit(String id, String name, String type) {
        EducationUnit unit = new EducationUnit();
        unit.setId(id);
        unit.setName(name);
        unit.setType(type);
        return unit;
    }

    @Test
    void aTenantWithMoreUnitsThanOnePagePicksTheSystemUnitBeforeAnyAlphabeticallyEarlierSchool() {
        Page<EducationUnit> firstPage = new PageImpl<>(List.of(unit("school-a", "A School", "school")), PageRequest.of(0, 1), 2);
        when(units.findDirectory(eq("erg"), any(), any(), any(Pageable.class))).thenReturn(firstPage);
        when(units.findByTenantIdOrderByNameAsc(eq("erg"), any(Pageable.class)))
                .thenReturn(List.of(unit("school-a", "A School", "school"), unit("hq", "Zed HQ", "system")));

        SessionBootstrapScope scope = resolver.resolveCurrentScopeTargeted(new UserAccessContext("u", "erg", List.of(), List.of(), null, null),
                caller);

        assertThat(scope.unitId()).isEqualTo("hq");
    }
}
