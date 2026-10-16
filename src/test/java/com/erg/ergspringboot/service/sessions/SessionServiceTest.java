package com.erg.ergspringboot.service.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.sessions.SessionResponses.SessionBootstrapResponse;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.exception.BadRequestException;
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
import java.util.stream.IntStream;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

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
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
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
        assertThat(response.portals()).containsExactly("lms");
        assertThat(response.workspaces()).containsExactly("TEACHER");
        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.lifecycle().status()).isEqualTo("FIRST_LOGIN_RESTRICTED");
        verify(educationUnitRepository, never()).findAllByOrderByNameAsc();
        verify(educationUnitRepository, never()).findByParentIdInOrderByNameAsc(any());
    }

    @Test
    void currentBootstrapReturnsWildcardPortalForSystemSuperAdmin() {
        SessionService service = new SessionService(
                sessionRepository,
                educationUnitRepository,
                classroomRepository,
                userPermissionOverrideRepository
        );
        User user = userWithRole("u-admin", "system.super_admin");
        Permission wildcard = new Permission();
        wildcard.setName("*");
        user.getRoles().iterator().next().setPermissions(new LinkedHashSet<>(List.of(wildcard)));
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(true);
        user.setMustChangePassword(false);
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-admin");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(sessionRepository.findWithUserBySessionIdAndTenantId("s-admin", "erg"))
                .thenReturn(Optional.of(session));
        when(educationUnitRepository.findTop500ByOrderByNameAsc()).thenReturn(List.of());

        SessionBootstrapResponse response = service.currentBootstrap(
                "s-admin",
                "erg",
                new UserAccessContext("u-admin", "erg", List.of(), List.of(), null, null)
        );

        assertThat(response.portals()).containsExactly("*");
        assertThat(response.workspaces()).containsExactly("ADMIN", "CRM", "STUDENT", "TEACHER");
        assertThat(response.tenantId()).isEqualTo("erg");
        assertThat(response.lifecycle().status()).isEqualTo("ACTIVE");
    }

    @Test
    void currentBootstrapProjectsGenericAdminToCanonicalAdminWorkspace() {
        SessionService service = new SessionService(
                sessionRepository,
                educationUnitRepository,
                classroomRepository,
                userPermissionOverrideRepository
        );
        User user = userWithRole("u-local-admin", "admin");
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(true);
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-local-admin");
        session.setTenantId("erg");
        session.setPortal("admin");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(sessionRepository.findWithUserBySessionIdAndTenantId("s-local-admin", "erg"))
                .thenReturn(Optional.of(session));

        SessionBootstrapResponse response = service.currentBootstrap(
                "s-local-admin",
                "erg",
                new UserAccessContext("u-local-admin", "erg", List.of(), List.of(), null, null)
        );

        assertThat(response.portals()).containsExactly("lcms");
        assertThat(response.workspaces()).containsExactly("ADMIN");
    }

    @Test
    void currentBootstrapRejectsMoreThanFiveHundredManageableClasses() {
        SessionService service = new SessionService(
                sessionRepository, educationUnitRepository, classroomRepository, userPermissionOverrideRepository);
        User user = userWithRole("u-admin", "system.super_admin");
        Permission wildcard = new Permission();
        wildcard.setName("*");
        user.getRoles().iterator().next().setPermissions(new LinkedHashSet<>(List.of(wildcard)));
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setSessionId("s-large");
        session.setTenantId("erg");
        session.setExpiresAt(OffsetDateTime.now().plusHours(1));
        EducationUnit school = educationUnit("school-1", "Large school", "school", null);
        List<Classroom> classes = IntStream.range(0, 501).mapToObj(index -> {
            Classroom classroom = new Classroom();
            classroom.setId("class-" + index);
            classroom.setSchoolId("school-1");
            classroom.setTenantId("erg");
            return classroom;
        }).toList();
        when(sessionRepository.findWithUserBySessionIdAndTenantId("s-large", "erg"))
                .thenReturn(Optional.of(session));
        when(educationUnitRepository.findTop500ByOrderByNameAsc()).thenReturn(List.of(school));
        when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                org.mockito.ArgumentMatchers.eq("erg"),
                org.mockito.ArgumentMatchers.eq(List.of("school-1")),
                any(Pageable.class)))
                .thenReturn(classes);

        assertThatThrownBy(() -> service.currentBootstrap(
                "s-large", "erg", new UserAccessContext("u-admin", "erg", List.of(), List.of(), null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("classes");
    }

    private User userWithRole(String userId, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(userId);
        user.setFullName("Teacher");
        user.setEmail("teacher@erg.edu.vn");
        user.setStatus("ACTIVE");
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
