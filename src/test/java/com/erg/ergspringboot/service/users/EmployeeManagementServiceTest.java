package com.erg.ergspringboot.service.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeProfileRequest;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.UpdateEmployeeRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserItemResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.EmployeeProfile;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.EmployeeProfileRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeManagementServiceTest {

    @Mock private UserService userService;
    @Mock private EmployeeProfileRepository profileRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserActivityRepository activityRepository;
    @Mock private AuthSessionRepository sessionRepository;
    @Mock private SecurityEventRepository securityEventRepository;
    @Mock private UserRoleAssignmentRepository roleAssignmentRepository;
    @Mock private ScopedRoleAssignmentService scopedRoleAssignmentService;
    private EmployeeManagementService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeManagementService(
                userService,
                profileRepository,
                userRepository,
                activityRepository,
                sessionRepository,
                securityEventRepository,
                roleAssignmentRepository,
                scopedRoleAssignmentService);
    }

    @Test
    void detailUsesTenantQualifiedAccountAndProfessionalProfile() {
        UserDetailResponse account = mock(UserDetailResponse.class);
        EmployeeProfile profile = profile("profile-1", "tenant-a", "user-1", "GV-001");
        when(userService.getUserDetail("user-1", "tenant-a")).thenReturn(account);
        when(profileRepository.findByUserIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(profile));

        var response = service.getEmployee("user-1", "tenant-a");

        assertThat(response.account()).isSameAs(account);
        assertThat(response.profile().employeeCode()).isEqualTo("GV-001");
        verify(profileRepository).findByUserIdAndTenantId("user-1", "tenant-a");
    }

    @Test
    void updatePersistsEditableBasicAndProfessionalFieldsWithAudit() {
        User user = new User();
        user.setId("user-1");
        user.setTenantId("tenant-a");
        user.setFullName("Old Name");
        EmployeeProfile profile = profile("profile-1", "tenant-a", "user-1", "GV-001");
        when(userRepository.findByIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(user));
        when(profileRepository.findByUserIdAndTenantId("user-1", "tenant-a")).thenReturn(Optional.of(profile));
        when(profileRepository.existsByTenantIdAndEmployeeCodeIgnoreCaseAndUserIdNot("tenant-a", "GV-002", "user-1"))
                .thenReturn(false);
        when(userService.getUserDetail("user-1", "tenant-a")).thenReturn(mock(UserDetailResponse.class));
        UpdateEmployeeRequest request = new UpdateEmployeeRequest(
                "Teacher Two",
                "0909000000",
                new EmployeeProfileRequest(
                        "GV-002", "training", "Senior Teacher", "FULL_TIME",
                        LocalDate.parse("2024-01-01"), null, new BigDecimal("7.5"),
                        "Seven years teaching", "manager-1", "HCM", Map.of("phone", "0911000000")
                )
        );

        var response = service.updateEmployee(
                "user-1",
                "tenant-a",
                request,
                new UserService.AuditContext("admin-1", "request-1", "127.0.0.1", "JUnit")
        );

        assertThat(user.getFullName()).isEqualTo("Teacher Two");
        assertThat(user.getPhone()).isEqualTo("0909000000");
        assertThat(profile.getEmployeeCode()).isEqualTo("GV-002");
        assertThat(response.profile().experienceYears()).isEqualByComparingTo("7.5");
        verify(userRepository).save(user);
        verify(profileRepository).save(profile);
        verify(activityRepository).save(ArgumentCaptor.forClass(com.erg.ergspringboot.model.users.UserActivity.class).capture());
    }

    @Test
    void listEnrichesProfileScopeActiveDevicesAndLatestRiskWithoutChangingPagination() {
        UserItemResponse user = new UserItemResponse(
                "user-1", "teacher@erg.edu.vn", "Teacher One", null, "0909", "ACTIVE",
                "internal", "employee", "password", List.of("lms_teacher"), true,
                "2026-07-15T08:00:00Z", "2024-01-01T00:00:00Z");
        when(userService.listUsers("tenant-a", 1, 25, null, null, null))
                .thenReturn(new UserListResponse(List.of(user), new Meta(1, 25, 1, 1)));
        EmployeeProfile profile = profile("profile-1", "tenant-a", "user-1", "GV-001");
        profile.setDepartmentId("training"); profile.setJobTitle("Senior Teacher");
        when(profileRepository.findByTenantIdAndUserIdIn("tenant-a", List.of("user-1")))
                .thenReturn(List.of(profile));
        AuthSession desktop = new AuthSession(); desktop.setUserId("user-1"); desktop.setPlatform("WEB_DESKTOP");
        AuthSession mobile = new AuthSession(); mobile.setUserId("user-1"); mobile.setPlatform("WEB_MOBILE");
        when(sessionRepository.findByUserIdInAndTenantIdAndRevokedAtIsNull(List.of("user-1"), "tenant-a"))
                .thenReturn(List.of(desktop, mobile));
        UserRoleAssignment assignment = new UserRoleAssignment(); assignment.setUserId("user-1");
        assignment.setScopeType("SCHOOL"); assignment.setScopeId("school-1");
        when(roleAssignmentRepository.findActiveForUsers(
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(List.of("user-1")),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(assignment));
        SecurityEvent risk = new SecurityEvent(); risk.setUserId("user-1");
        risk.setRiskCode("RAPID_SESSION_REPLACEMENT"); risk.setSeverity("HIGH");
        when(securityEventRepository.findByTenantIdAndUserIdInOrderByOccurredAtDesc(
                "tenant-a", List.of("user-1"))).thenReturn(List.of(risk));

        var response = service.listEmployees("tenant-a", 1, 25, null, null, null);

        assertThat(response.meta().total()).isEqualTo(1);
        assertThat(response.users()).singleElement().satisfies(employee -> {
            assertThat(employee.employeeCode()).isEqualTo("GV-001");
            assertThat(employee.scopes()).containsExactly("SCHOOL:school-1");
            assertThat(employee.activeDeviceCount()).isEqualTo(2);
            assertThat(employee.activePlatforms()).containsExactly("WEB_DESKTOP", "WEB_MOBILE");
            assertThat(employee.riskCode()).isEqualTo("RAPID_SESSION_REPLACEMENT");
        });
    }

    private EmployeeProfile profile(String id, String tenantId, String userId, String employeeCode) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setId(id);
        profile.setTenantId(tenantId);
        profile.setUserId(userId);
        profile.setEmployeeCode(employeeCode);
        return profile;
    }
}
