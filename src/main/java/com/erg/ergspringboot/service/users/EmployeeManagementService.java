package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeDetailResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeListItemResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeListResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.CreateEmployeeRequest;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeCreatedResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeProfileRequest;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.EmployeeProfileResponse;
import com.erg.ergspringboot.common.dto.users.EmployeeDtos.UpdateEmployeeRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.users.EmployeeProfile;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.common.response.users.UserResponses.UserItemResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.EmployeeProfileRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeManagementService {

    private final UserService userService;
    private final EmployeeProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final UserActivityRepository activityRepository;
    private final AuthSessionRepository sessionRepository;
    private final SecurityEventRepository securityEventRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ScopedRoleAssignmentService scopedRoleAssignmentService;

    public EmployeeManagementService(
            UserService userService,
            EmployeeProfileRepository profileRepository,
            UserRepository userRepository,
            UserActivityRepository activityRepository,
            AuthSessionRepository sessionRepository,
            SecurityEventRepository securityEventRepository,
            UserRoleAssignmentRepository roleAssignmentRepository,
            ScopedRoleAssignmentService scopedRoleAssignmentService
    ) {
        this.userService = userService;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.securityEventRepository = securityEventRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.scopedRoleAssignmentService = scopedRoleAssignmentService;
    }

    @Transactional(readOnly = true)
    public EmployeeListResponse listEmployees(
            String tenantId,
            Integer page,
            Integer limit,
            String search,
            String status,
            String role
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        UserListResponse base = userService.listUsers(normalizedTenant, page, limit, search, status, role);
        List<String> userIds = base.users().stream().map(UserItemResponse::id).toList();
        if (userIds.isEmpty()) return new EmployeeListResponse(List.of(), base.meta());

        Map<String, EmployeeProfile> profiles = profileRepository.findByTenantIdAndUserIdIn(normalizedTenant, userIds)
                .stream().collect(Collectors.toMap(EmployeeProfile::getUserId, Function.identity()));
        Map<String, List<AuthSession>> activeSessions = sessionRepository
                .findByUserIdInAndTenantIdAndRevokedAtIsNull(userIds, normalizedTenant)
                .stream().collect(Collectors.groupingBy(AuthSession::getUserId));
        Map<String, SecurityEvent> latestRisk = new LinkedHashMap<>();
        securityEventRepository.findByTenantIdAndUserIdInOrderByOccurredAtDesc(normalizedTenant, userIds)
                .stream()
                .filter(event -> event.getRiskCode() != null && !event.getRiskCode().isBlank())
                .forEach(event -> latestRisk.putIfAbsent(event.getUserId(), event));
        Map<String, List<UserRoleAssignment>> assignments = roleAssignmentRepository
                .findActiveForUsers(normalizedTenant, userIds, OffsetDateTime.now())
                .stream().collect(Collectors.groupingBy(UserRoleAssignment::getUserId));

        List<EmployeeListItemResponse> items = base.users().stream()
                .map(user -> toEmployeeListItem(
                        user,
                        profiles.get(user.id()),
                        activeSessions.getOrDefault(user.id(), List.of()),
                        assignments.getOrDefault(user.id(), List.of()),
                        latestRisk.get(user.id())))
                .toList();
        return new EmployeeListResponse(items, base.meta());
    }

    @Transactional
    public EmployeeCreatedResponse createEmployee(
            String tenantId,
            CreateEmployeeRequest request,
            UserService.AuditContext auditContext
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        String employeeCode = trimToNull(request.profile().employeeCode());
        if (employeeCode == null) throw new BadRequestException("employee code is required");
        if (profileRepository.existsByTenantIdAndEmployeeCodeIgnoreCase(normalizedTenant, employeeCode)) {
            throw new BadRequestException("employee code already exists in tenant");
        }
        UserCreatedResponse account = userService.createUser(new AdminCreateUserRequest(
                request.primaryEmail(), request.fullName(), request.phone(), request.recoveryEmail(),
                request.roleGroupIds(), List.of(), request.credentialDelivery()), auditContext);
        request.policies().forEach(policy -> scopedRoleAssignmentService.assign(
                account.user().id(),
                normalizedTenant,
                new AssignScopedRoleRequest(policy.roleGroup(), policy.scopeType(), policy.scopeId(), null),
                SecurityUtils.requirePrincipal(),
                auditContext));
        EmployeeProfile profile = upsertProfile(account.user().id(), normalizedTenant, request.profile());
        User user = userRepository.findByIdAndTenantId(account.user().id(), normalizedTenant)
                .orElseThrow(() -> new ResourceNotFoundException("created employee not found"));
        recordCreatedAudit(user, profile, auditContext);
        return new EmployeeCreatedResponse(account, toProfileResponse(profile));
    }

    @Transactional(readOnly = true)
    public EmployeeDetailResponse getEmployee(String userId, String tenantId) {
        String normalizedTenant = normalizeTenant(tenantId);
        return new EmployeeDetailResponse(
                userService.getUserDetail(userId, normalizedTenant),
                profileRepository.findByUserIdAndTenantId(userId, normalizedTenant)
                        .map(this::toProfileResponse)
                        .orElse(null)
        );
    }

    @Transactional
    public EmployeeDetailResponse updateEmployee(
            String userId,
            String tenantId,
            UpdateEmployeeRequest request,
            UserService.AuditContext auditContext
    ) {
        String normalizedTenant = normalizeTenant(tenantId);
        User user = userRepository.findByIdAndTenantId(userId, normalizedTenant)
                .orElseThrow(() -> new ResourceNotFoundException("employee not found"));
        Map<String, Object> before = snapshot(user, null);
        if (request.fullName() != null) user.setFullName(request.fullName().trim());
        if (request.phone() != null) user.setPhone(trimToNull(request.phone()));
        userRepository.save(user);

        EmployeeProfile profile = null;
        if (request.profile() != null) {
            profile = upsertProfile(userId, normalizedTenant, request.profile());
        }
        recordAudit(user, auditContext, before, snapshot(user, profile));
        return new EmployeeDetailResponse(
                userService.getUserDetail(userId, normalizedTenant),
                profile == null
                        ? profileRepository.findByUserIdAndTenantId(userId, normalizedTenant)
                                .map(this::toProfileResponse).orElse(null)
                        : toProfileResponse(profile)
        );
    }

    private EmployeeProfile upsertProfile(String userId, String tenantId, EmployeeProfileRequest request) {
        EmployeeProfile profile = profileRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseGet(() -> {
                    EmployeeProfile created = new EmployeeProfile();
                    created.setId(IdGenerator.newId());
                    created.setTenantId(tenantId);
                    created.setUserId(userId);
                    return created;
                });
        String employeeCode = trimToNull(request.employeeCode());
        if (employeeCode == null && profile.getEmployeeCode() == null) {
            throw new BadRequestException("employee code is required for a new profile");
        }
        if (employeeCode != null
                && profileRepository.existsByTenantIdAndEmployeeCodeIgnoreCaseAndUserIdNot(tenantId, employeeCode, userId)) {
            throw new BadRequestException("employee code already exists in tenant");
        }
        if (employeeCode != null) profile.setEmployeeCode(employeeCode);
        profile.setDepartmentId(trimToNull(request.departmentId()));
        profile.setJobTitle(trimToNull(request.jobTitle()));
        profile.setEmploymentType(trimToNull(request.employmentType()));
        profile.setJoinedAt(request.joinedAt());
        profile.setTerminatedAt(request.terminatedAt());
        profile.setExperienceYears(request.experienceYears());
        profile.setExperienceSummary(trimToNull(request.experienceSummary()));
        profile.setManagerId(trimToNull(request.managerId()));
        profile.setWorkLocation(trimToNull(request.workLocation()));
        profile.setEmergencyContact(request.emergencyContact() == null ? Map.of() : request.emergencyContact());
        return profileRepository.save(profile);
    }

    private void recordAudit(
            User target,
            UserService.AuditContext context,
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setTenantId(target.getTenantId());
        activity.setUserId(context == null || context.actorUserId() == null ? target.getId() : context.actorUserId());
        activity.setTargetUserId(target.getId());
        activity.setAction("EMPLOYEE_UPDATED");
        activity.setDescription("Employee account and professional profile updated");
        activity.setRequestId(context == null ? null : context.requestId());
        activity.setIpAddress(context == null ? null : context.ipAddress());
        activity.setUserAgent(context == null ? null : context.userAgent());
        activity.setMetadata(Map.of("before", before, "after", after));
        activity.setCreatedAt(OffsetDateTime.now());
        activityRepository.save(activity);
    }

    private void recordCreatedAudit(
            User target,
            EmployeeProfile profile,
            UserService.AuditContext context
    ) {
        UserActivity activity = new UserActivity();
        activity.setId(IdGenerator.newId());
        activity.setTenantId(target.getTenantId());
        activity.setUserId(context == null || context.actorUserId() == null ? target.getId() : context.actorUserId());
        activity.setTargetUserId(target.getId());
        activity.setAction("EMPLOYEE_PROFILE_CREATED");
        activity.setDescription("Employee account and professional profile created");
        activity.setRequestId(context == null ? null : context.requestId());
        activity.setIpAddress(context == null ? null : context.ipAddress());
        activity.setUserAgent(context == null ? null : context.userAgent());
        activity.setMetadata(Map.of("profile", snapshot(target, profile)));
        activity.setCreatedAt(OffsetDateTime.now());
        activityRepository.save(activity);
    }

    private Map<String, Object> snapshot(User user, EmployeeProfile profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fullName", user.getFullName());
        snapshot.put("phone", user.getPhone());
        if (profile != null) {
            snapshot.put("employeeCode", profile.getEmployeeCode());
            snapshot.put("departmentId", profile.getDepartmentId());
            snapshot.put("jobTitle", profile.getJobTitle());
            snapshot.put("employmentType", profile.getEmploymentType());
            snapshot.put("experienceYears", profile.getExperienceYears());
            snapshot.put("managerId", profile.getManagerId());
        }
        return snapshot;
    }

    private EmployeeProfileResponse toProfileResponse(EmployeeProfile profile) {
        return new EmployeeProfileResponse(
                profile.getId(), profile.getUserId(), profile.getTenantId(), profile.getEmployeeCode(),
                profile.getDepartmentId(), profile.getJobTitle(), profile.getEmploymentType(), profile.getJoinedAt(),
                profile.getTerminatedAt(), profile.getExperienceYears(), profile.getExperienceSummary(),
                profile.getManagerId(), profile.getWorkLocation(),
                profile.getEmergencyContact() == null ? Map.of() : profile.getEmergencyContact()
        );
    }

    private EmployeeListItemResponse toEmployeeListItem(
            UserItemResponse user,
            EmployeeProfile profile,
            List<AuthSession> sessions,
            List<UserRoleAssignment> assignments,
            SecurityEvent risk
    ) {
        List<String> platforms = sessions.stream()
                .map(AuthSession::getPlatform)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
        List<String> scopes = assignments.stream()
                .map(assignment -> assignment.getScopeType()
                        + (assignment.getScopeId() == null ? "" : ":" + assignment.getScopeId()))
                .distinct()
                .toList();
        return new EmployeeListItemResponse(
                user.id(), user.email(), user.fullName(), user.avatarUrl(), user.phone(), user.status(),
                user.roles(), user.lastLoginAt(), user.createdAt(),
                profile == null ? null : profile.getEmployeeCode(),
                profile == null ? null : profile.getDepartmentId(),
                profile == null ? null : profile.getJobTitle(),
                profile == null ? null : profile.getEmploymentType(),
                scopes,
                sessions.size(),
                platforms,
                risk == null ? null : risk.getRiskCode(),
                risk == null ? null : risk.getSeverity()
        );
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
