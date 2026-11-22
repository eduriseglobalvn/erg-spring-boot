package com.erg.ergspringboot.lms.internal.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.StudentCredentialResetResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.users.CredentialHasher;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StudentCredentialResetServiceTest {

    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final UserActivityRepository activities = mock(UserActivityRepository.class);
    private final CredentialHasher hasher = mock(CredentialHasher.class);
    private final com.erg.ergspringboot.lms.LmsCallerPermissions permissions =
            mock(com.erg.ergspringboot.lms.LmsCallerPermissions.class);
    private final StudentCredentialResetService service = new StudentCredentialResetService(
            classrooms, students, users, assignments, sessions, activities, hasher,
            mock(com.erg.ergspringboot.users.persistence.auth.AuthLoginAttemptRepository.class), permissions,
            new com.erg.ergspringboot.platform.security.ErgAuthProperties());

    @Test
    void schoolScopedStaffNeedTheStudentManagePermission() {
        givenStudentInClass("student.workspace.access");
        UserAccessContext schoolStaff = new UserAccessContext("pdt-1", "t1", List.of("s1"), List.of(), null, null);
        assertThrows(ForbiddenException.class, () -> service.reset(schoolStaff, "c1", "stu-1", null, null, null));
        when(permissions.hasAny("lms.student.manage", "*")).thenReturn(true);
        when(hasher.hash(any())).thenReturn("hash");
        org.junit.jupiter.api.Assertions.assertNotNull(service.reset(schoolStaff, "c1", "stu-1", null, null, null));
    }

    private UserAccessContext teacherOf(String classId) {
        return new UserAccessContext("teacher-1", "t1", List.of(), List.of(classId), null, null);
    }

    private void givenStudentInClass(String workspacePermission) {
        Classroom classroom = new Classroom();
        classroom.setId("c1");
        classroom.setSchoolId("s1");
        when(classrooms.findByTenantIdAndId("t1", "c1")).thenReturn(Optional.of(classroom));
        Student student = new Student();
        student.setId("stu-1");
        student.setAcademicClassId("c1");
        student.setAuthUserId("u1");
        when(students.findByTenantIdAndId("t1", "stu-1")).thenReturn(Optional.of(student));
        User user = new User();
        user.setId("u1");
        user.setUsername("hs01");
        user.setMustChangePassword(true);
        when(users.findByIdAndTenantId("u1", "t1")).thenReturn(Optional.of(user));
        Permission permission = new Permission();
        permission.setName(workspacePermission);
        Role role = new Role();
        role.setName("role");
        role.getPermissions().add(permission);
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role);
        when(assignments.findActiveForUser(any(), any(), any())).thenReturn(List.of(assignment));
        when(hasher.hash(any())).thenReturn("hashed");
        when(sessions.findByUserIdAndTenantIdAndRevokedAtIsNull("u1", "t1")).thenReturn(List.of(new AuthSession()));
    }

    @Test
    void resetAlwaysSetsTheConfiguredDefaultPasswordNotTheUsername() {
        givenStudentInClass("student.workspace.access");
        User user = users.findByIdAndTenantId("u1", "t1").orElseThrow();
        user.setUsername("thcstcd.nguyenvana0123");
        assertEquals("erg2026", service.reset(teacherOf("c1"), "c1", "stu-1", null, null, null).password());
    }

    @Test
    void teacherOfTheClassGetsTheDefaultPasswordAndSessionsAreRevoked() {
        givenStudentInClass("student.workspace.access");
        StudentCredentialResetResponse response = service.reset(teacherOf("c1"), "c1", "stu-1", "req", "1.1.1.1", "ua");
        assertEquals("hs01", response.username());
        assertEquals("erg2026", response.password());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("hashed", saved.getValue().getPasswordHash());
        assertFalse(saved.getValue().getMustChangePassword());
        ArgumentCaptor<List<AuthSession>> revoked = ArgumentCaptor.forClass(List.class);
        verify(sessions).saveAll(revoked.capture());
        assertNotNull(revoked.getValue().getFirst().getRevokedAt());
        ArgumentCaptor<UserActivity> audit = ArgumentCaptor.forClass(UserActivity.class);
        verify(activities).save(audit.capture());
        assertFalse(audit.getValue().getMetadata().containsValue(response.password()));
    }

    @Test
    void teacherOfAnotherClassIsRefused() {
        givenStudentInClass("student.workspace.access");
        assertThrows(ForbiddenException.class, () -> service.reset(teacherOf("c2"), "c1", "stu-1", null, null, null));
        verify(users, never()).save(any());
    }

    @Test
    void aStaffAccountLinkedToAStudentRecordIsRefused() {
        givenStudentInClass("teacher.workspace.access");
        assertThrows(ForbiddenException.class, () -> service.reset(teacherOf("c1"), "c1", "stu-1", null, null, null));
        verify(users, never()).save(any());
    }
}
