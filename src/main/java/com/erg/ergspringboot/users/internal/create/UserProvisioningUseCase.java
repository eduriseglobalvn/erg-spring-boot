package com.erg.ergspringboot.users.internal.create;

import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.CredentialDeliveryResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ProvisionedUserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ProtectedRolePolicy;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns account provisioning, invitation and temporary credential rotation. */
@Service
public class UserProvisioningUseCase {
    private static final String DEFAULT_TENANT = "erg";
    private final UserRepository users; private final AuthSessionRepository sessions;
    private final RoleRepository roles; private final UserAccessScopeRepository scopes;
    private final UserActivityRepository activities; private final PasswordService passwords;
    private final AccountMailSender mail; private final ProtectedRolePolicy rolePolicy = new ProtectedRolePolicy();

    public UserProvisioningUseCase(UserRepository users, AuthSessionRepository sessions, RoleRepository roles,
            UserAccessScopeRepository scopes, UserActivityRepository activities,
            PasswordService passwords, AccountMailSender mail) {
        this.users = users; this.sessions = sessions; this.roles = roles; this.scopes = scopes;
        this.activities = activities; this.passwords = passwords; this.mail = mail;
    }

    @Transactional
    public UserCreatedResponse create(AdminCreateUserRequest request, UserAuditContext audit, ErgPrincipal actor) {
        String email = request.primaryEmail().trim().toLowerCase();
        String recovery = optionalEmail(request.recoveryEmail());
        if (email.equals(recovery)) throw new BadRequestException("recovery email must differ from primary email");
        String delivery = delivery(request.credentialDelivery());
        if (users.existsByUsernameIgnoreCase(email)) throw new ConflictException("username already registered");
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("email already registered");
        String credential = IdGenerator.newId() + "Aa1!";
        List<String> requestedRoles = request.roleGroupIds().stream().map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
        List<Role> assignedRoles = roles.findByNameInIgnoreCase(requestedRoles);
        if (assignedRoles.size() != requestedRoles.size())
            throw new BadRequestException("one or more role groups were not found");
        rolePolicy.assertCanGrant(actor, assignedRoles);
        Set<String> requestedSet = new LinkedHashSet<>(requestedRoles);
        if (request.policies().stream().anyMatch(policy -> !requestedSet.contains(policy.roleGroup())))
            throw new BadRequestException("policy role group must be assigned to the user");
        User user = new User(); user.setId(IdGenerator.newId()); user.setUsername(email); user.setEmail(email);
        user.setPasswordHash(passwords.hash(credential)); user.setFullName(trim(request.fullName()));
        user.setPhone(trim(request.phone())); user.setRecoveryEmail(recovery); user.setRecoveryEmailVerified(false);
        user.setMustChangePassword(true); user.setStatus("INVITED"); user.setProvider("local");
        user.setAccountType("erg"); user.setTenantId(actor == null ? DEFAULT_TENANT : tenant(actor.tenantId()));
        user.setIsProfileCompleted(!blank(user.getFullName()) && !blank(user.getPhone()));
        user.setLoginCount(0L); user.setRoles(new LinkedHashSet<>(assignedRoles)); users.save(user);
        List<UserAccessScope> savedScopes = request.policies().stream().map(policy -> {
            UserAccessScope scope = new UserAccessScope(); scope.setId(IdGenerator.newId()); scope.setUserId(user.getId());
            scope.setEducationUnitId(policy.scopeId().trim()); scope.setRole(policy.roleGroup().trim());
            scope.setModules(policy.modules().stream().map(String::trim).filter(v -> !v.isBlank()).distinct()
                    .collect(Collectors.joining(","))); return scope;
        }).toList();
        scopes.saveAll(savedScopes);
        if ("EMAIL_INVITE".equals(delivery)) mail.sendTeacherInvite(email, user.getFullName(), credential);
        record(user, audit, "TEACHER_ACCOUNT_PROVISIONED", "Teacher account provisioned", Map.of(),
                Map.of("status", user.getStatus(), "credentialDelivery", delivery,
                        "roleGroups", requestedRoles, "scopeCount", savedScopes.size()));
        return new UserCreatedResponse(new ProvisionedUserResponse(user.getId(), email, user.getFullName(), user.getStatus()),
                lifecycle(user), "EMAIL_INVITE".equals(delivery) ? "QUEUED_FOR_DELIVERY" : "READY",
                "ONE_TIME_PASSWORD".equals(delivery) ? credential : null);
    }

    @Transactional
    public CredentialDeliveryResponse reset(String userId, String tenantId, String requestedDelivery,
            UserAuditContext audit) {
        User user = users.findByIdAndTenantId(userId, tenant(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        String delivery = delivery(requestedDelivery);
        boolean previous = Boolean.TRUE.equals(user.getMustChangePassword());
        String credential = IdGenerator.newId() + "Aa1!";
        user.setPasswordHash(passwords.hash(credential)); user.setMustChangePassword(true); users.save(user);
        List<AuthSession> active = sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenant(tenantId));
        OffsetDateTime revokedAt = OffsetDateTime.now(ZoneOffset.UTC);
        active.forEach(session -> { session.setRevokedAt(revokedAt); session.setRevokedReason("credential_reset"); });
        if (!active.isEmpty()) sessions.saveAll(active);
        if ("EMAIL_INVITE".equals(delivery)) mail.sendTeacherInvite(user.getEmail(), user.getFullName(), credential);
        record(user, audit, "EMAIL_INVITE".equals(delivery) ? "TEACHER_INVITE_RESENT" : "TEACHER_CREDENTIAL_RESET",
                "Teacher temporary credential rotated", Map.of("mustChangePassword", previous),
                Map.of("mustChangePassword", true, "credentialDelivery", delivery, "revokedSessions", active.size()));
        return new CredentialDeliveryResponse("EMAIL_INVITE".equals(delivery) ? "QUEUED_FOR_DELIVERY" : "READY",
                "ONE_TIME_PASSWORD".equals(delivery) ? credential : null);
    }

    private void record(User target, UserAuditContext audit, String action, String description,
            Map<String,Object> before, Map<String,Object> after) {
        UserAuditContext context = audit == null ? new UserAuditContext(target.getId(), null, null, null) : audit;
        UserActivity a = new UserActivity(); a.setId(IdGenerator.newId());
        a.setUserId(blank(context.actorUserId()) ? target.getId() : context.actorUserId());
        a.setTenantId(tenant(target.getTenantId())); a.setAction(action); a.setTargetUserId(target.getId());
        a.setIpAddress(trim(context.ipAddress())); a.setUserAgent(trim(context.userAgent()));
        a.setDescription(description); a.setRequestId(trim(context.requestId()));
        a.setMetadata(Map.of("before",before,"after",after)); a.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        activities.save(a);
    }
    private AccountLifecycleResponse lifecycle(User user) { List<String> next = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) next.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) next.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) next.add("CHANGE_PASSWORD");
        String status = next.isEmpty()?"ACTIVE":("INVITED".equalsIgnoreCase(user.getStatus())?"PROVISIONED":"FIRST_LOGIN_RESTRICTED");
        String e=user.getRecoveryEmail(); String masked=e==null||!e.contains("@")?null:e.substring(0,1)+"***"+e.substring(e.indexOf('@'));
        return new AccountLifecycleResponse(status,Boolean.TRUE.equals(user.getIsProfileCompleted()),masked,
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()),Boolean.TRUE.equals(user.getMustChangePassword()),List.copyOf(next)); }
    private String delivery(String v) { String d=v==null?"":v.trim().toUpperCase();
        if(!List.of("EMAIL_INVITE","ONE_TIME_PASSWORD").contains(d)) throw new BadRequestException("invalid credential delivery"); return d; }
    private String optionalEmail(String v){String n=trim(v);return n==null?null:n.toLowerCase();}
    private String tenant(String v){return blank(v)?DEFAULT_TENANT:v.trim();}
    private String trim(String v){return blank(v)?null:v.trim();}
    private boolean blank(String v){return v==null||v.isBlank();}
}
