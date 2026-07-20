package com.erg.ergspringboot.authorization;

import com.erg.ergspringboot.service.users.UserService;

/** Boundary audit metadata; legacy service types do not cross the module API. */
public record AuthorizationAuditContext(
        String actorUserId,
        String requestId,
        String ipAddress,
        String userAgent
) {
    public UserService.AuditContext toLegacy() {
        return new UserService.AuditContext(actorUserId, requestId, ipAddress, userAgent);
    }
}
