package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.request.users.UserRequests.*;
import com.erg.ergspringboot.common.response.users.UserResponses.*;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.service.users.UserService;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public users API. The legacy constructor is retained only for focused compatibility tests. */
@Service
public class UserOperations {
    private final UserQueryUseCase queries; private final UserProfileUseCase profiles;
    private final UserPasswordUseCase passwords; private final UserSessionUseCase sessions;
    private final UserProvisioningUseCase provisioning; private final UserLifecycleUseCase lifecycle;
    private final UserRoleAssignmentUseCase roles; private final LegacyAdapter legacy;

    @Autowired
    public UserOperations(UserQueryUseCase queries, UserProfileUseCase profiles, UserPasswordUseCase passwords,
            UserSessionUseCase sessions, UserProvisioningUseCase provisioning, UserLifecycleUseCase lifecycle,
            UserRoleAssignmentUseCase roles) {
        this.queries=queries;this.profiles=profiles;this.passwords=passwords;this.sessions=sessions;this.provisioning=provisioning;this.lifecycle=lifecycle;this.roles=roles;this.legacy=null;
    }
    public UserOperations(UserService legacy) {
        this.queries=null;this.profiles=null;this.passwords=null;this.sessions=null;this.provisioning=null;this.lifecycle=null;this.roles=null;this.legacy=new LegacyAdapter(legacy);
    }
    public UserResponse current(String id,String tenant){return legacy!=null?legacy.current(id,tenant):queries.current(id,tenant);}
    public UserResponse updateProfile(String id,String tenant,UpdateProfileRequest request,UserAuditContext audit){return legacy!=null?legacy.updateProfile(id,tenant,request,audit):profiles.update(id,tenant,request,audit);}
    public OnboardingResponse completeOnboarding(String id,String tenant,OnboardingRequest request){return legacy!=null?legacy.completeOnboarding(id,tenant,request):profiles.completeOnboarding(id,tenant,request);}
    public PasswordChangeResponse changePassword(String id,String tenant,String session,ChangePasswordRequest request,UserAuditContext audit){return legacy!=null?legacy.changePassword(id,tenant,session,request,audit):passwords.change(id,tenant,session,request,audit);}
    public SessionListResponse listSessions(String id,String tenant,String session){return legacy!=null?legacy.listSessions(id,tenant,session):sessions.list(id,tenant,session);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session){return legacy!=null?legacy.revokeSession(id,tenant,session):sessions.revoke(id,tenant,session,null);}
    public RevokeSessionResponse revokeSession(String id,String tenant,String session,UserAuditContext audit){return legacy!=null?legacy.revokeSession(id,tenant,session,audit):sessions.revoke(id,tenant,session,audit);}
    public UserListResponse listUsers(String tenant,Integer page,Integer limit,String search,String status,String role){return legacy!=null?legacy.listUsers(tenant,page,limit,search,status,role):queries.list(tenant,page,limit,search,status,role);}
    public UserCreatedResponse createUser(AdminCreateUserRequest request,UserAuditContext audit){return legacy!=null?legacy.createUser(request,audit):provisioning.create(request,audit,SecurityUtils.principal());}
    public CredentialDeliveryResponse resetCredential(String id,String tenant,String delivery,UserAuditContext audit){return legacy!=null?legacy.resetCredential(id,tenant,delivery,audit):provisioning.reset(id,tenant,delivery,audit);}
    public BulkOperationResponse bulkUpdateStatus(String tenant,BulkStatusRequest request){return legacy!=null?legacy.bulkUpdateStatus(tenant,request):lifecycle.bulkStatus(tenant,request);}
    public BulkOperationResponse bulkDelete(String tenant,BulkDeleteRequest request){return legacy!=null?legacy.bulkDelete(tenant,request):lifecycle.bulkDelete(tenant,request);}
    public UserDetailResponse getUserDetail(String id,String tenant){return legacy!=null?legacy.getUserDetail(id,tenant):queries.detail(id,tenant);}
    public ActivityListResponse listActivity(String id,String tenant,Integer page,Integer limit){return legacy!=null?legacy.listActivity(id,tenant,page,limit):queries.activity(id,tenant,page,limit);}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request){return legacy!=null?legacy.updateUserStatus(id,tenant,request):lifecycle.updateStatus(id,tenant,request,null);}
    public UserResponse updateUserStatus(String id,String tenant,AdminUpdateUserStatusRequest request,UserAuditContext audit){return legacy!=null?legacy.updateUserStatus(id,tenant,request,audit):lifecycle.updateStatus(id,tenant,request,audit);}
    public UserResponse assignRoles(String id,String tenant,AdminAssignRolesRequest request,UserAuditContext audit){return legacy!=null?legacy.assignRoles(id,tenant,request,audit):roles.assignRoles(id,tenant,request,audit,SecurityUtils.principal());}
    public PermissionOverrideResponse assignPermissions(String id,String tenant,AdminAssignPermissionsRequest request,UserAuditContext audit){return legacy!=null?legacy.assignPermissions(id,tenant,request,audit):roles.assignPermissions(id,tenant,request,audit);}
    public BulkOperationResponse deleteUser(String id,String tenant){return legacy!=null?legacy.deleteUser(id,tenant):lifecycle.delete(id,tenant);}

    private static final class LegacyAdapter {
        private final UserService service; LegacyAdapter(UserService service){this.service=service;}
        UserResponse current(String i,String t){return service.getCurrentUser(i,t);} UserResponse updateProfile(String i,String t,UpdateProfileRequest r,UserAuditContext a){return service.updateProfile(i,t,r,a.toLegacy());}
        OnboardingResponse completeOnboarding(String i,String t,OnboardingRequest r){return service.completeOnboarding(i,t,r);} PasswordChangeResponse changePassword(String i,String t,String s,ChangePasswordRequest r,UserAuditContext a){return service.changePassword(i,t,s,r,a.toLegacy());}
        SessionListResponse listSessions(String i,String t,String s){return service.listSessions(i,t,s);} RevokeSessionResponse revokeSession(String i,String t,String s){return service.revokeSession(i,t,s);} RevokeSessionResponse revokeSession(String i,String t,String s,UserAuditContext a){return service.revokeSession(i,t,s,a.toLegacy());}
        UserListResponse listUsers(String t,Integer p,Integer l,String q,String st,String r){return service.listUsers(t,p,l,q,st,r);} UserCreatedResponse createUser(AdminCreateUserRequest r,UserAuditContext a){return service.createUser(r,a.toLegacy());}
        CredentialDeliveryResponse resetCredential(String i,String t,String d,UserAuditContext a){return service.resetCredential(i,t,d,a.toLegacy());} BulkOperationResponse bulkUpdateStatus(String t,BulkStatusRequest r){return service.bulkUpdateStatus(t,r);} BulkOperationResponse bulkDelete(String t,BulkDeleteRequest r){return service.bulkDelete(t,r);}
        UserDetailResponse getUserDetail(String i,String t){return service.getUserDetail(i,t);} ActivityListResponse listActivity(String i,String t,Integer p,Integer l){return service.listActivity(i,t,p,l);} UserResponse updateUserStatus(String i,String t,AdminUpdateUserStatusRequest r){return service.updateUserStatus(i,t,r);} UserResponse updateUserStatus(String i,String t,AdminUpdateUserStatusRequest r,UserAuditContext a){return service.updateUserStatus(i,t,r,a.toLegacy());}
        UserResponse assignRoles(String i,String t,AdminAssignRolesRequest r,UserAuditContext a){return service.assignRoles(i,t,r,a.toLegacy());} PermissionOverrideResponse assignPermissions(String i,String t,AdminAssignPermissionsRequest r,UserAuditContext a){return service.assignPermissions(i,t,r,a.toLegacy());} BulkOperationResponse deleteUser(String i,String t){return service.deleteUser(i,t);}
    }
}
