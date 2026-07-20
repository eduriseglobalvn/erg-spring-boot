package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.service.users.UserService;
import org.springframework.stereotype.Service;

/** Compatibility facade for the first users vertical slice. */
@Service
public class UserOperations {
    private final UserService legacy;

    public UserOperations(UserService legacy) {
        this.legacy = legacy;
    }

    public UserResponse current(String userId, String tenantId) {
        return legacy.getCurrentUser(userId, tenantId);
    }

    public UserResponse updateProfile(String userId, String tenantId, UpdateProfileRequest request,
            UserService.AuditContext auditContext) {
        return legacy.updateProfile(userId, tenantId, request, auditContext);
    }
}
