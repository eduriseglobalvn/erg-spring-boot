package com.erg.ergspringboot.service.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private AuthSessionRepository sessionRepository;
    @Mock
    private EducationUnitRepository educationUnitRepository;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Test
    void currentBootstrapLoadsOnlyTeacherManagedEducationUnitsForTeacher() {
        SessionService service = new SessionService(
                sessionRepository,
                educationUnitRepository,
                classroomRepository,
                userPermissionOverrideRepository
        );
        User user = userWithRole("u-teacher", "teacher");
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s1");
        session.setTenantId("erg");
        EducationUnit school = educationUnit("school-1", "THCS Binh An", "school", null);

        when(sessionRepository.findWithUserBySessionIdAndTenantId("s1", "erg")).thenReturn(Optional.of(session));
        when(classroomRepository.findTeacherManagedSchoolIds("erg", "u-teacher")).thenReturn(List.of("school-1"));
        when(educationUnitRepository.findByIdInOrderByNameAsc(any(Collection.class))).thenReturn(List.of(school));

        SessionBootstrapResponse response = service.currentBootstrap(
                "s1",
                "erg",
                new UserAccessContext("u-teacher", "erg", List.of(), List.of(), null, null)
        );

        assertThat(response.manageableUnits()).hasSize(1);
        assertThat(response.manageableUnits().getFirst().id()).isEqualTo("school-1");
        verify(educationUnitRepository, never()).findAllByOrderByNameAsc();
        verify(educationUnitRepository, never()).findByParentIdInOrderByNameAsc(any());
    }

    private User userWithRole(String userId, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(userId);
        user.setFullName("Teacher");
        user.setEmail("teacher@erg.edu.vn");
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        return user;
    }

    private EducationUnit educationUnit(String id, String name, String type, String parentId) {
        EducationUnit educationUnit = new EducationUnit();
        educationUnit.setId(id);
        educationUnit.setName(name);
        educationUnit.setType(type);
        educationUnit.setParentId(parentId);
        return educationUnit;
    }
}
