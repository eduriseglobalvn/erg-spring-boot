package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.users.UserAuditContext;

/** Boundary audit metadata; legacy service types do not cross the module API. */
public record AuthorizationAuditContext(
        String actorUserId,
        String requestId,
        String ipAddress,
        String userAgent
) {
    UserAuditContext toLegacy() {
        return new UserAuditContext(actorUserId, requestId, ipAddress, userAgent);
    }
}
