package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.users.UserAuditContext;

/** Request audit metadata owned by the auth boundary. */
public record AuthAuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) {
    UserAuditContext toLegacy() {
        return new UserAuditContext(actorUserId, requestId, ipAddress, userAgent);
    }
}
