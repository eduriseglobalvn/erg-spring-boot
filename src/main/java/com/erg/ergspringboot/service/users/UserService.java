package com.erg.ergspringboot.service.users;

import com.erg.ergspringboot.common.request.users.UserRequests.*;
import com.erg.ergspringboot.common.response.users.UserResponses.*;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.*;
import com.erg.ergspringboot.repository.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.service.authorization.AuthorizationMutationAuditService;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Compatibility facade; user behavior is owned by internal use cases. */
@Service
public class UserService {
    private static final String DEFAULT_TENANT="erg";
    private final UserQueryUseCase queries; private final UserProfileUseCase profiles;
    private final UserPasswordUseCase passwords; private final UserSessionUseCase sessions;
    private final UserProvisioningUseCase provisioning; private final UserLifecycleUseCase lifecycle;
    private final UserRoleAssignmentUseCase roles;

    @Autowired
    public UserService(UserQueryUseCase queries,UserProfileUseCase profiles,UserPasswordUseCase passwords,
            UserSessionUseCase sessions,UserProvisioningUseCase provisioning,UserLifecycleUseCase lifecycle,
            UserRoleAssignmentUseCase roles){this.queries=queries;this.profiles=profiles;this.passwords=passwords;this.sessions=sessions;this.provisioning=provisioning;this.lifecycle=lifecycle;this.roles=roles;}

    /** Compatibility constructor retained for legacy tests/callers. */
    public UserService(UserRepository users,AuthSessionRepository sessions,RoleRepository roles,
            PermissionRepository permissions,UserPermissionOverrideRepository overrides,
            UserActivityRepository activities,PasswordService passwordService,
            UserAccessScopeRepository scopes,AccountMailSender mail){
        this(new UserQueryUseCase(users,sessions,activities),new UserProfileUseCase(users,activities),
                new UserPasswordUseCase(users,sessions,activities,passwordService),new UserSessionUseCase(sessions,activities),
                new UserProvisioningUseCase(users,sessions,roles,scopes,activities,passwordService,mail),
                new UserLifecycleUseCase(users,sessions,activities),
                new UserRoleAssignmentUseCase(users,roles,permissions,overrides,activities,null));
    }

    @Autowired(required=false) void setAuthorizationMutationAuditService(AuthorizationMutationAuditService value){roles.setAuthorizationMutationAuditService(value);}

    public UserResponse getCurrentUser(String id,String tenant){return queries.current(id,tenant);}
    public UserResponse updateProfile(String id,String tenant,UpdateProfileRequest request){return updateProfile(id,tenant,request,null);}
    public UserResponse updateProfile(String id,String tenant,UpdateProfileRequest request,AuditContext audit){return profiles.update(id,tenant,request,publicAudit(audit));}
    public OnboardingResponse completeOnboarding(String id,String tenant,OnboardingRequest request){return profiles.completeOnboarding(id,tenant,request);}
    public SessionListResponse listSessions(String id,String tenant,String current){return sessions.list(id,tenant,current);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session){return revokeSession(id,tenant,session,null);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session,AuditContext audit){return sessions.revoke(id,tenant,session,publicAudit(audit));}
    public PasswordChangeResponse changePassword(String id,String tenant,ChangePasswordRequest request){return changePassword(id,tenant,null,request);}
    public PasswordChangeResponse changePassword(String id,String tenant,String session,ChangePasswordRequest request){return changePassword(id,tenant,session,request,null);}
    public PasswordChangeResponse changePassword(String id,String tenant,String session,ChangePasswordRequest request,AuditContext audit){return passwords.change(id,tenant,session,request,publicAudit(audit));}
    public UserListResponse listUsers(Integer page,Integer limit,String search,String status,String role){return listUsers(DEFAULT_TENANT,page,limit,search,status,role);}
    public UserListResponse listUsers(String tenant,Integer page,Integer limit,String search,String status,String role){return queries.list(tenant,page,limit,search,status,role);}
    public UserListResponse listUsers(ListUsersQuery query){return listUsers(query.page(),query.limit(),query.search(),query.status(),query.role());}
    public UserDetailResponse getUserDetail(String id,String tenant){return queries.detail(id,tenant);}
    public ActivityListResponse listActivity(String id,String tenant,Integer page,Integer limit){return queries.activity(id,tenant,page,limit);}
    public UserCreatedResponse createUser(AdminCreateUserRequest request){return createUser(request,null);}
    public UserCreatedResponse createUser(AdminCreateUserRequest request,AuditContext audit){return createUser(request,audit,SecurityUtils.principal());}
    public UserCreatedResponse createUser(AdminCreateUserRequest request,AuditContext audit,ErgPrincipal actor){return provisioning.create(request,publicAudit(audit),actor);}
    public CredentialDeliveryResponse resetCredential(String id,String tenant,String delivery){return resetCredential(id,tenant,delivery,null);}
    public CredentialDeliveryResponse resetCredential(String id,String tenant,String delivery,AuditContext audit){return provisioning.reset(id,tenant,delivery,publicAudit(audit));}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request){return updateUserStatus(id,tenant,request,null);}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request,AuditContext audit){return lifecycle.updateStatus(id,tenant,request,publicAudit(audit));}
    public BulkOperationResponse deleteUser(String id,String tenant){return lifecycle.delete(id,tenant);}
    public BulkOperationResponse bulkUpdateStatus(String tenant,BulkStatusRequest request){return lifecycle.bulkStatus(tenant,request);}
    public BulkOperationResponse bulkDelete(String tenant,BulkDeleteRequest request){return lifecycle.bulkDelete(tenant,request);}
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request){return assignRoles(id,tenant,request,null);}
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request,AuditContext audit){return assignRoles(id,tenant,request,audit,SecurityUtils.principal());}
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request,AuditContext audit,ErgPrincipal actor){return roles.assignRoles(id,tenant,request,publicAudit(audit),actor);}
    public PermissionOverrideResponse assignPermissions(String id,String tenant,AdminAssignPermissionsRequest request){return assignPermissions(id,tenant,request,null);}
    public PermissionOverrideResponse assignPermissions(String id,String tenant,AdminAssignPermissionsRequest request,AuditContext audit){return roles.assignPermissions(id,tenant,request,publicAudit(audit));}

    public record AuditContext(String actorUserId,String requestId,String ipAddress,String userAgent){}
    private UserAuditContext publicAudit(AuditContext audit){return audit==null?null:new UserAuditContext(audit.actorUserId(),audit.requestId(),audit.ipAddress(),audit.userAgent());}
}
