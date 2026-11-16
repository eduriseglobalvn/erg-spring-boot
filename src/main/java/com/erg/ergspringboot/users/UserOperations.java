package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.request.users.UserRequests.*;
import com.erg.ergspringboot.common.response.users.UserResponses.*;
import com.erg.ergspringboot.platform.security.AccountActionGuard;
import com.erg.ergspringboot.platform.security.AccountActionGuard.AccountAction;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public users API. */
@Service
public class UserOperations {
    private final UserQueryUseCase queries; private final UserProfileUseCase profiles;
    private final UserPasswordUseCase passwords; private final UserSessionUseCase sessions;
    private final UserProvisioningUseCase provisioning; private final UserLifecycleUseCase lifecycle;
    private final UserRoleAssignmentUseCase roles; private final AccountActionGuard guard;

    @Autowired
    public UserOperations(UserQueryUseCase queries, UserProfileUseCase profiles, UserPasswordUseCase passwords,
            UserSessionUseCase sessions, UserProvisioningUseCase provisioning, UserLifecycleUseCase lifecycle,
            UserRoleAssignmentUseCase roles, AccountActionGuard guard) {
        this.guard=guard;this.queries=queries;this.profiles=profiles;this.passwords=passwords;this.sessions=sessions;this.provisioning=provisioning;this.lifecycle=lifecycle;this.roles=roles;
    }
    public UserResponse current(String id,String tenant){return queries.current(id,tenant);}
    public UserResponse updateProfile(String id,String tenant,UpdateProfileRequest request,UserAuditContext audit){return profiles.update(id,tenant,request,audit);}
    public OnboardingResponse completeOnboarding(String id,String tenant,OnboardingRequest request){return profiles.completeOnboarding(id,tenant,request);}
    public PasswordChangeResponse changePassword(String id,String tenant,String session,ChangePasswordRequest request,UserAuditContext audit){return passwords.change(id,tenant,session,request,audit);}
    public SessionListResponse listSessions(String id,String tenant,String session){return sessions.list(id,tenant,session);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session){return sessions.revoke(id,tenant,session,null);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session,UserAuditContext audit){guard.assertCanActOn(SecurityUtils.principal(),id,AccountAction.REVOKE_SESSIONS);return sessions.revoke(id,tenant,session,audit);}
    public UserListResponse listUsers(String tenant,Integer page,Integer limit,String search,String status,String role){return queries.list(tenant,page,limit,search,status,role);}
    public UserCreatedResponse createUser(AdminCreateUserRequest request,UserAuditContext audit){return createUser(request,audit,SecurityUtils.principal());}
    public UserCreatedResponse createUser(AdminCreateUserRequest request,UserAuditContext audit,com.erg.ergspringboot.platform.security.ErgPrincipal actor){return provisioning.create(request,audit,actor);}
    public CredentialDeliveryResponse resetCredential(String id,String tenant,String delivery,UserAuditContext audit){guard.assertCanActOn(SecurityUtils.principal(),id,"EMAIL_INVITE".equalsIgnoreCase(delivery)?AccountAction.RESEND_INVITE:AccountAction.RESET_PASSWORD);return provisioning.reset(id,tenant,delivery,audit);}
    public BulkOperationResponse bulkUpdateStatus(String tenant,BulkStatusRequest request){guard.assertCanActOnAll(SecurityUtils.principal(),request.userIds(),statusAction(request.status()));return lifecycle.bulkStatus(tenant,request);}
    public BulkOperationResponse bulkDelete(String tenant,BulkDeleteRequest request){return bulkDelete(tenant,request,null);}
    public BulkOperationResponse bulkDelete(String tenant,BulkDeleteRequest request,UserAuditContext audit){guard.assertCanActOnAll(SecurityUtils.principal(),request.userIds(),AccountAction.DELETE);return lifecycle.bulkDelete(tenant,request,audit);}
    public UserDetailResponse getUserDetail(String id,String tenant){return queries.detail(id,tenant);}
    public ActivityListResponse listActivity(String id,String tenant,Integer page,Integer limit){return queries.activity(id,tenant,page,limit);}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request){return updateUserStatus(id,tenant,request,null);}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request,UserAuditContext audit){guard.assertCanActOn(SecurityUtils.principal(),id,statusAction(request.status()));return lifecycle.updateStatus(id,tenant,request,audit);}
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request,UserAuditContext audit){
        return roles.assignRoles(id, tenant, request, audit, SecurityUtils.principal());
    }
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request,UserAuditContext audit,com.erg.ergspringboot.platform.security.ErgPrincipal actor){return roles.assignRoles(id,tenant,request,audit,actor);}
    public BulkOperationResponse deleteUser(String id,String tenant){return deleteUser(id,tenant,null);}
    public BulkOperationResponse deleteUser(String id,String tenant,UserAuditContext audit){guard.assertCanActOn(SecurityUtils.principal(),id,AccountAction.DELETE);return lifecycle.delete(id,tenant,audit);}
    /** Undo a soft delete while the 30-day window is open. */
    public UserResponse restoreUser(String id,String tenant,UserAuditContext audit){guard.assertCanActOn(SecurityUtils.principal(),id,AccountAction.UNLOCK);return lifecycle.restore(id,tenant,audit);}
    private static AccountAction statusAction(String status){return status!=null&&"ACTIVE".equalsIgnoreCase(status.trim())?AccountAction.UNLOCK:AccountAction.LOCK;}
}
