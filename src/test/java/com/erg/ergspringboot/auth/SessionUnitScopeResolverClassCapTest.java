package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class SessionUnitScopeResolverClassCapTest {

    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final SessionUnitScopeResolver resolver = new SessionUnitScopeResolver(
            mock(EducationUnitRepository.class), classrooms, mock(UserRoleAssignmentRepository.class));
    private final List<SessionUnitScopeResolver.ManagedUnit> unit =
            List.of(new SessionUnitScopeResolver.ManagedUnit("s1", "school", "School", null));

    private void stubClasses(int count) {
        List<Classroom> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Classroom c = new Classroom();
            c.setId("c" + i);
            c.setName("Class " + i);
            c.setSchoolId("s1");
            list.add(c);
        }
        when(classrooms.findByTenantIdAndSchoolIdInOrderByNameAsc(eq("erg"), anyList(), any(Pageable.class))).thenReturn(list);
    }

    @Test
    void cutAtTheCapSetsTheTruncatedFlag() {
        stubClasses(501);

        SessionUnitScopeResolver.ManagedClasses result = resolver.resolveManageableClasses("erg", unit, null);

        assertThat(result.rows()).hasSize(500);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void atTheCapNothingIsFlagged() {
        stubClasses(500);

        SessionUnitScopeResolver.ManagedClasses result = resolver.resolveManageableClasses("erg", unit, null);

        assertThat(result.rows()).hasSize(500);
        assertThat(result.truncated()).isFalse();
    }
}
