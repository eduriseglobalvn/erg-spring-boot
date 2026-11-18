package com.erg.ergspringboot.users.internal.lifecycle;

import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.UserAuditContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns account status and deletion lifecycle commands. */
@Service
public class UserLifecycleUseCase {
    private static final String DEFAULT_TENANT = "erg";
    static final String STATUS_DELETED = "DELETED";
    static final long RESTORE_WINDOW_DAYS = 30;
    private final UserRepository users; private final AuthSessionRepository sessions;
    private final UserActivityRepository activities; private final UserSoftDeleteStore softDeletes;
    public UserLifecycleUseCase(UserRepository users, AuthSessionRepository sessions, UserActivityRepository activities,
            UserSoftDeleteStore softDeletes) {
        this.users=users; this.sessions=sessions; this.activities=activities; this.softDeletes=softDeletes;
    }
    @Transactional
    public UserResponse updateStatus(String userId, String tenantId, AdminUpdateUserStatusRequest request,
            UserAuditContext audit) {
        String status=status(request.status()); User user=find(userId,tenantId); String previous=safe(user.getStatus());
        user.setStatus(status); users.save(user); revokeIfLocked(user,tenant(tenantId),status);
        record(user,audit,Map.of("status",previous),Map.of("status",status)); return response(user);
    }
    /** Soft delete: status DELETED, sessions revoked, password cleared; PII is anonymised by {@link UserPurgeJob} after 30 days. */
    @Transactional public BulkOperationResponse delete(String userId,String tenantId){return delete(userId,tenantId,null);}
    @Transactional public BulkOperationResponse delete(String userId,String tenantId,UserAuditContext audit){
        softDelete(List.of(find(userId,tenantId)),tenant(tenantId),audit);return new BulkOperationResponse(1);}
    /** Undo a soft delete inside the window. The account comes back INACTIVE and must have its credential reset. */
    @Transactional public UserResponse restore(String userId,String tenantId,UserAuditContext audit){
        User user=users.findByIdAndTenantId(userId,tenant(tenantId)).filter(u->STATUS_DELETED.equalsIgnoreCase(u.getStatus()))
                .orElseThrow(()->new ResourceNotFoundException("user not found"));
        OffsetDateTime until=softDeletes.purgeAfter(userId).filter(t->t.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .orElseThrow(()->new BadRequestException("restore window has passed"));
        user.setStatus("INACTIVE"); user.setMustChangePassword(true); user.setAuthzVersion((user.getAuthzVersion()==null?1L:user.getAuthzVersion())+1);
        users.save(user); softDeletes.restore(userId);
        record(user,audit,"USER_RESTORED","Employee account restored",Map.of("status",STATUS_DELETED,"restorableUntil",until.toString()),Map.of("status","INACTIVE"));
        return response(user);}
    @Transactional public BulkOperationResponse bulkStatus(String tenantId,BulkStatusRequest request){
        String status=status(request.status()); List<User> found=findAll(request.userIds(),tenantId);
        found.forEach(user->user.setStatus(status)); users.saveAll(found); String t=tenant(tenantId);
        found.forEach(user->revokeIfLocked(user,t,status)); return new BulkOperationResponse(found.size()); }
    @Transactional public BulkOperationResponse bulkDelete(String tenantId,BulkDeleteRequest request){return bulkDelete(tenantId,request,null);}
    @Transactional public BulkOperationResponse bulkDelete(String tenantId,BulkDeleteRequest request,UserAuditContext audit){
        List<User> found=findAll(request.userIds(),tenantId); softDelete(found,tenant(tenantId),audit); return new BulkOperationResponse(found.size()); }
    private void softDelete(List<User> found,String tenant,UserAuditContext audit){
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC); OffsetDateTime purgeAfter=now.plusDays(RESTORE_WINDOW_DAYS);
        Map<String,String> previous=found.stream().collect(Collectors.toMap(User::getId,u->safe(u.getStatus()),(a,b)->a));
        found.forEach(user->{user.setStatus(STATUS_DELETED); user.setPasswordHash(null);
            user.setAuthzVersion((user.getAuthzVersion()==null?1L:user.getAuthzVersion())+1);});
        users.saveAll(found); softDeletes.mark(found.stream().map(User::getId).toList(),now,purgeAfter);
        found.forEach(user->{revokeIfLocked(user,tenant,STATUS_DELETED);
            record(user,audit,"USER_DELETED","Employee account deleted",Map.of("status",previous.get(user.getId())),
                    Map.of("status",STATUS_DELETED,"restorableUntil",purgeAfter.toString()));}); }
    static void anonymise(User u){
        u.setStatus(STATUS_DELETED); u.setEmail("deleted-"+u.getId()+"@deleted.invalid"); u.setUsername(null);
        u.setPasswordHash(null); u.setFullName("Deleted user"); u.setAvatarUrl(null); u.setPhone(null); u.setBio(null);
        u.setGender(null); u.setDateOfBirth(null); u.setAddress(null); u.setCity(null); u.setDistrict(null);
        u.setJobTitle(null); u.setRegion(null); u.setSocialLinks(null); u.setExtendedProfile(null);
        u.setRecoveryEmail(null); u.setRecoveryEmailVerified(false); u.setMustChangePassword(false);
        u.setAuthzVersion((u.getAuthzVersion()==null?1L:u.getAuthzVersion())+1); }
    private void revokeIfLocked(User user,String tenant,String status){
        if(!List.of("BLOCKED","BANNED","INACTIVE",STATUS_DELETED).contains(status))return;
        List<AuthSession> active=sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(),tenant); if(active.isEmpty())return;
        OffsetDateTime at=OffsetDateTime.now(ZoneOffset.UTC); String reason="status_changed_"+status.toLowerCase();
        active.forEach(s->{s.setRevokedAt(at);s.setRevokedReason(reason);});sessions.saveAll(active);}
    private void record(User target,UserAuditContext audit,Map<String,Object> before,Map<String,Object> after){
        record(target,audit,"EMPLOYEE_STATUS_CHANGED","Employee account status changed",before,after);}
    private void record(User target,UserAuditContext audit,String action,String description,Map<String,Object> before,Map<String,Object> after){
        UserAuditContext c=audit==null?new UserAuditContext(target.getId(),null,null,null):audit;
        UserActivity a=new UserActivity();a.setId(IdGenerator.newId());a.setUserId(blank(c.actorUserId())?target.getId():c.actorUserId());
        a.setTenantId(tenant(target.getTenantId()));a.setAction(action);a.setTargetUserId(target.getId());
        a.setIpAddress(trim(c.ipAddress()));a.setUserAgent(trim(c.userAgent()));a.setRequestId(trim(c.requestId()));
        a.setDescription(description);a.setMetadata(Map.of("before",before,"after",after));
        a.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));activities.save(a);}
    private User find(String id,String tenant){return users.findByIdAndTenantId(id,tenant(tenant))
            .filter(u->!STATUS_DELETED.equalsIgnoreCase(u.getStatus()))
            .orElseThrow(()->new ResourceNotFoundException("user not found"));}
    private List<User> findAll(List<String> ids,String tenant){return ids.stream().map(id->find(id,tenant)).toList();}
    private String status(String value){String s=value.trim().toUpperCase();
        if(!List.of("ACTIVE","PENDING","BLOCKED","BANNED","INACTIVE").contains(s))throw new BadRequestException("invalid user status");return s;}
    private UserResponse response(User u){return new UserResponse(u.getId(),u.getEmail(),u.getFullName(),u.getPhone(),u.getAvatarUrl(),
            u.getBio(),u.getGender(),u.getDateOfBirth(),u.getAddress(),u.getCity(),u.getDistrict(),u.getJobTitle(),u.getRegion(),
            u.getStatus(),u.getProvider(),u.getAccountType(),u.getLastLoginProvider(),roles(u.getRoles()),
            u.getSocialLinks()==null?Map.of():u.getSocialLinks(),Boolean.TRUE.equals(u.getIsProfileCompleted()),iso(u.getLastLoginAt()),
            u.getLoginCount(),u.getTenantId(),iso(u.getCreatedAt()),iso(u.getUpdatedAt()),lifecycle(u));}
    private AccountLifecycleResponse lifecycle(User u){List<String> next=new ArrayList<>();
        next.addAll(u.firstLoginSteps());
        String state=next.isEmpty()?"ACTIVE":("INVITED".equalsIgnoreCase(u.getStatus())?"PROVISIONED":"FIRST_LOGIN_RESTRICTED");
        String e=u.getRecoveryEmail();String masked=e==null||!e.contains("@")?null:e.substring(0,1)+"***"+e.substring(e.indexOf('@'));
        return new AccountLifecycleResponse(state,Boolean.TRUE.equals(u.getIsProfileCompleted()),masked,
                Boolean.TRUE.equals(u.getRecoveryEmailVerified()),Boolean.TRUE.equals(u.getMustChangePassword()),List.copyOf(next));}
    private List<String> roles(Set<Role> roles){if(roles==null||roles.isEmpty())return List.of("user");return roles.stream()
            .map(Role::getName).filter(n->n!=null&&!n.isBlank()).collect(Collectors.toList());}
    private String tenant(String v){return blank(v)?DEFAULT_TENANT:v.trim();}private String trim(String v){return blank(v)?null:v.trim();}
    private String safe(String v){return v==null?"":v;}private String iso(OffsetDateTime v){return v==null?null:v.toString();}
    private boolean blank(String v){return v==null||v.isBlank();}
}
