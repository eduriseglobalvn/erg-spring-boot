package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.service.users.UserService;

/** Request audit metadata owned by the auth boundary. */
public record AuthAuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) {
    public UserService.AuditContext toLegacy() {
        return new UserService.AuditContext(actorUserId, requestId, ipAddress, userAgent);
    }
}
